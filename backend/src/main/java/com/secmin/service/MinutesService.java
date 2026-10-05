package com.secmin.service;

import com.secmin.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 纪要生命周期：
 *  - 记录员提交/修订：正文 + 附件 + 建议接收人名单在同一次请求、同一事务中成为一个版本；
 *  - 负责人审批：可调整最终接收名单并预览，审批时冻结快照、生成定向授权、发提醒邮件；
 *  - 负责人撤回：版本 WITHDRAWN，旧链接立即失效，历史分发记录保留；
 *  - 管理员撤销个人授权：grant.revoked=true，旧链接立即失效；
 *  - 转岗：更新员工当前岗位与密级，下一次打开链接实时核验即拒绝。
 */
@ApplicationScoped
public class MinutesService {

    @Inject
    EntityManager em;

    @Inject
    FileStorageService fileStorage;

    @Inject
    MailService mailService;

    public record SubmittedFile(String fileName, String contentType, byte[] content) {}

    @Transactional
    public Minutes createMinutes(String title, Long secretaryId) {
        Minutes m = new Minutes();
        m.setTitle(title);
        m.setCreatedById(secretaryId);
        m.setCreatedAt(Instant.now());
        em.persist(m);
        em.flush();
        return m;
    }

    /**
     * 原子提交一个版本。bodyText 与 files 同事务落库落盘，任一失败整体回滚
     * （文件落盘后若事务回滚，catch 中丢弃整个批次目录）。
     */
    @Transactional
    public MinutesVersion submitVersion(Long minutesId, Classification classification, String bodyText,
                                        String changeNote, List<SubmittedFile> files,
                                        List<Long> proposedRecipientIds, Long secretaryId) {
        Objects.requireNonNull(bodyText, "正文不能为空");
        if (bodyText.isBlank()) {
            throw new IllegalArgumentException("正文不能为空");
        }
        Objects.requireNonNull(classification, "密级不能为空");
        if (files == null) {
            files = List.of();
        }
        Minutes minutes = em.find(Minutes.class, minutesId);
        if (minutes == null) {
            throw new IllegalArgumentException("纪要不存在");
        }

        String batchId = UUID.randomUUID().toString();
        try {
            int nextNo = minutes.getCurrentVersionNo() + 1;
            MinutesVersion v = new MinutesVersion();
            v.setMinutesId(minutesId);
            v.setVersionNo(nextNo);
            v.setClassification(classification);
            v.setBodyText(bodyText);
            v.setChangeNote(changeNote);
            v.setState(VersionState.PENDING);
            v.setCreatedAt(Instant.now());
            v.setSubmittedById(secretaryId);
            em.persist(v);
            em.flush();

            for (SubmittedFile f : files) {
                String storagePath = fileStorage.store(batchId, f.fileName(), f.content());
                Attachment a = new Attachment();
                a.setVersionId(v.getId());
                a.setFileName(f.fileName());
                a.setContentType(f.contentType() == null ? "application/octet-stream" : f.contentType());
                a.setStoragePath(storagePath);
                a.setSizeBytes(f.content() == null ? 0 : f.content().length);
                em.persist(a);
            }

            // 建议名单：校验人员存在、启用且岗位密级满足本版本密级（不满足的不予建议，审批页给出提示）
            List<Long> valid = new ArrayList<>();
            for (Long uid : proposedRecipientIds == null ? List.<Long>of() : proposedRecipientIds) {
                AppUser u = em.find(AppUser.class, uid);
                if (u != null && u.isEnabled()
                        && u.getClearanceLevel() >= classification.getLevel()
                        && !valid.contains(uid)) {
                    valid.add(uid);
                }
            }
            // 建议名单暂存在审批人可见的临时表，见 ProposedRecipient
            for (Long uid : valid) {
                ProposedRecipient pr = new ProposedRecipient();
                pr.setVersionId(v.getId());
                pr.setUserId(uid);
                em.persist(pr);
            }

            minutes.setCurrentVersionNo(nextNo);
            em.flush();
            return v;
        } catch (RuntimeException e) {
            fileStorage.discardBatch(batchId);
            throw e;
        }
    }

    /**
     * 负责人审批：最终接收名单可在建议名单基础上增删，但必须满足密级要求。
     * 审批通过瞬间：旧 APPROVED 版本变 SUPERSEDED；新版本冻结名单快照、建授权、发邮件。
     */
    @Transactional
    public MinutesVersion approve(Long versionId, List<Long> finalRecipientIds, Long approverId) {
        MinutesVersion v = em.find(MinutesVersion.class, versionId);
        if (v == null) {
            throw new IllegalArgumentException("版本不存在");
        }
        if (v.getState() != VersionState.PENDING) {
            throw new IllegalStateException("仅待审批版本可审批，当前状态: " + v.getState());
        }
        List<Long> finalIds = new ArrayList<>(new LinkedHashSet<>(
                finalRecipientIds == null ? List.of() : finalRecipientIds));
        List<AppUser> recipients = new ArrayList<>();
        for (Long uid : finalIds) {
            AppUser u = em.find(AppUser.class, uid);
            if (u == null || !u.isEnabled()) {
                throw new IllegalArgumentException("接收人不存在或已停用: " + uid);
            }
            if (u.getClearanceLevel() < v.getClassification().getLevel()) {
                throw new IllegalArgumentException(
                        "接收人 " + u.getName() + " 岗位密级低于本纪要密级，不可加入名单");
            }
            recipients.add(u);
        }
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("最终分发名单不能为空");
        }

        // 同纪要旧的已审批版本置为 SUPERSEDED，旧链接立即失效
        em.createQuery("update MinutesVersion x set x.state = :sup "
                        + "where x.minutesId = :mid and x.state = :app")
                .setParameter("sup", VersionState.SUPERSEDED)
                .setParameter("mid", v.getMinutesId())
                .setParameter("app", VersionState.APPROVED)
                .executeUpdate();

        v.setState(VersionState.APPROVED);
        v.setReviewedById(approverId);
        v.setReviewedAt(Instant.now());
        v.setApprovedRecipientIds(AccessControlService.joinIdList(finalIds));

        Minutes minutes = em.find(Minutes.class, v.getMinutesId());
        for (AppUser u : recipients) {
            // 同一版本同一人只保留一条有效授权（重复审批幂等）
            DistributionGrant existing = em.createQuery(
                            "select g from DistributionGrant g where g.versionId = :v and g.userId = :u",
                            DistributionGrant.class)
                    .setParameter("v", v.getId())
                    .setParameter("u", u.getId())
                    .getResultList().stream().findFirst().orElse(null);
            DistributionGrant grant;
            if (existing == null) {
                grant = new DistributionGrant();
                grant.setVersionId(v.getId());
                grant.setMinutesId(minutes.getId());
                grant.setUserId(u.getId());
                grant.setToken(newToken());
                grant.setCreatedAt(Instant.now());
                em.persist(grant);
                em.flush();
            } else {
                grant = existing;
                grant.setRevoked(false);
                grant.setRevokedAt(null);
            }
            mailService.sendDistributionMail(u, minutes, v, grant);
        }

        AuditActionLog(v, approverId, AuditAction.APPROVE, "审批通过，最终接收人 " + finalIds.size() + " 人");
        return v;
    }

    /**
     * 负责人撤回审批：版本置 WITHDRAWN。该版本上所有旧链接立即失效；
     * 历史授权、审计、邮件记录全部保留。
     */
    @Transactional
    public MinutesVersion withdraw(Long versionId, Long approverId) {
        MinutesVersion v = em.find(MinutesVersion.class, versionId);
        if (v == null) {
            throw new IllegalArgumentException("版本不存在");
        }
        if (v.getState() != VersionState.APPROVED) {
            throw new IllegalStateException("仅已审批版本可撤回，当前状态: " + v.getState());
        }
        v.setState(VersionState.WITHDRAWN);
        v.setReviewedById(approverId);
        v.setReviewedAt(Instant.now());
        AuditActionLog(v, approverId, AuditAction.WITHDRAW, "负责人撤回审批，全部旧链接立即失效");

        // 通知接收人（不含正文）
        Minutes minutes = em.find(Minutes.class, v.getMinutesId());
        for (Long uid : AccessControlService.parseIdList(
                v.getApprovedRecipientIds() == null ? "" : v.getApprovedRecipientIds())) {
            AppUser u = em.find(AppUser.class, uid);
            if (u != null) {
                mailService.sendAccessInvalidatedMail(u, minutes, "负责人已撤回该纪要审批");
            }
        }
        return v;
    }

    /**
     * 管理员撤销某个人的定向授权。旧链接立即失效，记录保留。
     */
    @Transactional
    public void revokeGrant(Long grantId, Long adminId) {
        DistributionGrant g = em.find(DistributionGrant.class, grantId);
        if (g == null) {
            throw new IllegalArgumentException("分发记录不存在");
        }
        g.setRevoked(true);
        g.setRevokedAt(Instant.now());
        MinutesVersion v = em.find(MinutesVersion.class, g.getVersionId());
        AuditActionLog(v, adminId, AuditAction.REVOKE_GRANT,
                "撤销用户 " + g.getUserId() + " 对版本 " + g.getVersionId() + " 的定向授权");
        AppUser u = em.find(AppUser.class, g.getUserId());
        Minutes m = em.find(Minutes.class, g.getMinutesId());
        if (u != null && m != null) {
            mailService.sendAccessInvalidatedMail(u, m, "管理员撤销了您的定向访问授权");
        }
    }

    /**
     * 员工转岗：更新当前岗位与密级。访问实时核验读取的就是这里写入的值，
     * 因此转岗降级后无需改动任何链接，旧链接自动失效。
     */
    @Transactional
    public AppUser transferPost(Long userId, String newPost, int newClearanceLevel, Long adminId) {
        AppUser u = em.find(AppUser.class, userId);
        if (u == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        Classification.ofLevel(newClearanceLevel);
        String old = u.getCurrentPost() + "/" + Classification.ofLevel(u.getClearanceLevel()).getLabel();
        u.setCurrentPost(newPost);
        u.setClearanceLevel(newClearanceLevel);
        u.setPostUpdatedAt(Instant.now());

        // 对因此不再满足密级的已审批纪要发送失效通知（通知里仍不含正文）
        String reason = "您已转岗（" + old + " → " + newPost + "/"
                + Classification.ofLevel(newClearanceLevel).getLabel() + "）";
        List<DistributionGrant> grants = em.createQuery(
                        "select g from DistributionGrant g where g.userId = :u and g.revoked = false",
                        DistributionGrant.class)
                .setParameter("u", userId)
                .getResultList();
        for (DistributionGrant g : grants) {
            MinutesVersion v = em.find(MinutesVersion.class, g.getVersionId());
            if (v != null && v.getState() == VersionState.APPROVED
                    && newClearanceLevel < v.getClassification().getLevel()) {
                mailService.sendAccessInvalidatedMail(u, em.find(Minutes.class, g.getMinutesId()),
                        reason + "，密级不再满足，旧链接已失效");
            }
        }
        return u;
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + Long.toHexString(System.nanoTime());
    }

    private void AuditActionLog(MinutesVersion v, Long operatorId, AuditAction action, String reason) {
        AccessAudit a = new AccessAudit();
        a.setAt(Instant.now());
        a.setUserId(operatorId);
        a.setMinutesId(v.getMinutesId());
        a.setVersionId(v.getId());
        a.setAction(action);
        a.setResult(AuditResult.GRANTED);
        a.setReason(reason);
        em.persist(a);
    }
}
