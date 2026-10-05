package com.secmin.service;

import com.secmin.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 访问控制核心。
 *
 * 员工每次打开链接都重新核验，绝不依赖“邮件发过=永久有权”：
 * 1) token 对应的分发记录必须存在；
 * 2) 当前登录人就是该授权的本人（专属链接，转发他人即拒绝）；
 * 3) 该条定向授权未被管理员撤销（revoked）；
 * 4) 版本处于 APPROVED —— 负责人撤回（WITHDRAWN）、版本被替代（SUPERSEDED）都会失效；
 * 5) 员工仍在审批冻结的最终接收名单快照内；
 * 6) 员工当前岗位密级（实时读取，转岗即变）&gt;= 版本密级；
 * 7) 账号处于启用状态。
 *
 * 校验本身为非事务只读；放行/拒绝审计分别在 {@link AuditService} 的独立短事务中提交，
 * 因此拒绝记录不会因随后抛出异常而回滚丢失（SQLite 下也避免嵌套事务锁冲突）。
 */
@ApplicationScoped
public class AccessControlService {

    @Inject
    EntityManager em;

    @Inject
    AuditService auditService;

    public static class AccessContext {
        public final DistributionGrant grant;
        public final MinutesVersion version;
        public final Minutes minutes;
        public final AppUser user;

        public AccessContext(DistributionGrant grant, MinutesVersion version,
                             Minutes minutes, AppUser user) {
            this.grant = grant;
            this.version = version;
            this.minutes = minutes;
            this.user = user;
        }
    }

    /**
     * 实时核验 token，返回访问上下文；失败记录 DENIED 审计并抛 {@link AccessDeniedException}。
     */
    public AccessContext authorize(String token, AppUser currentUser,
                                   AuditAction action, Long attachmentId) {
        DistributionGrant grant = token == null ? null
                : em.createQuery("select g from DistributionGrant g where g.token = :t",
                        DistributionGrant.class)
                        .setParameter("t", token)
                        .getResultList().stream().findFirst().orElse(null);

        if (grant == null) {
            return deny(null, null, null, currentUser, action, attachmentId, token, "链接无效或不存在");
        }

        AppUser user = em.find(AppUser.class, grant.getUserId());
        MinutesVersion version = em.find(MinutesVersion.class, grant.getVersionId());
        Minutes minutes = em.find(Minutes.class, grant.getMinutesId());

        // 专属链接：持链人必须是该授权的本人，防止转发扩散
        if (currentUser == null) {
            return deny(grant, minutes, version, user, action, attachmentId, token, "请先登录后再打开链接");
        }
        if (user == null || !user.getId().equals(currentUser.getId())) {
            return deny(grant, minutes, version, currentUser, action, attachmentId, token,
                    "该链接为其他员工的专属访问链接，非本人无权访问");
        }
        if (!user.isEnabled()) {
            return deny(grant, minutes, version, user, action, attachmentId, token, "账号已停用");
        }
        if (grant.isRevoked()) {
            return deny(grant, minutes, version, user, action, attachmentId, token,
                    "您的定向访问授权已被撤销");
        }
        if (version == null) {
            return deny(grant, minutes, null, user, action, attachmentId, token, "纪要版本不存在");
        }
        switch (version.getState()) {
            case WITHDRAWN -> {
                return deny(grant, minutes, version, user, action, attachmentId, token,
                        "负责人已撤回该纪要审批，链接立即失效");
            }
            case SUPERSEDED -> {
                return deny(grant, minutes, version, user, action, attachmentId, token,
                        "该版本已被新版替代，旧链接失效");
            }
            case PENDING -> {
                return deny(grant, minutes, version, user, action, attachmentId, token,
                        "该版本尚未审批通过");
            }
            default -> { /* APPROVED 继续核验 */ }
        }
        if (!inApprovedSnapshot(version, user.getId())) {
            return deny(grant, minutes, version, user, action, attachmentId, token,
                    "您不在最终审批接收名单内");
        }
        if (user.getClearanceLevel() < version.getClassification().getLevel()) {
            return deny(grant, minutes, version, user, action, attachmentId, token,
                    "转岗后当前岗位密级（" + Classification.ofLevel(user.getClearanceLevel()).getLabel()
                            + "）低于纪要密级（" + version.getClassification().getLabel() + "），访问被拒绝");
        }

        // 放行：刷新最近访问时间 + 审计，独立事务提交
        auditService.recordGranted(grant, user, minutes, version, attachmentId, action);
        return new AccessContext(grant, version, minutes, user);
    }

    /**
     * 附件下载额外校验：附件必须属于本次授权的同一版本，杜绝拿 A 纪要链接读 B 附件。
     */
    public Attachment authorizeAttachment(String token, AppUser currentUser, Long attachmentId) {
        AccessContext ctx = authorize(token, currentUser, AuditAction.DOWNLOAD_ATTACHMENT, attachmentId);
        Attachment att = em.find(Attachment.class, attachmentId);
        if (att == null || !att.getVersionId().equals(ctx.version.getId())) {
            auditService.record(ctx.user, ctx.minutes, ctx.version, attachmentId,
                    AuditAction.DOWNLOAD_ATTACHMENT, AuditResult.DENIED,
                    "附件不属于当前授权版本（疑似越权读取其他文件）", token);
            throw new AccessDeniedException("附件不属于当前授权版本，已拒绝下载", token, ctx.user.getId());
        }
        return att;
    }

    public boolean inApprovedSnapshot(MinutesVersion version, Long userId) {
        if (version.getApprovedRecipientIds() == null || version.getApprovedRecipientIds().isBlank()) {
            return false;
        }
        return parseIdList(version.getApprovedRecipientIds()).contains(userId);
    }

    public static Set<Long> parseIdList(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    public static String joinIdList(java.util.Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private AccessContext deny(DistributionGrant grant, Minutes minutes, MinutesVersion version,
                               AppUser user, AuditAction action, Long attachmentId,
                               String token, String reason) {
        auditService.record(user, minutes, version, attachmentId,
                action, AuditResult.DENIED, reason, token);
        throw new AccessDeniedException(reason, token, user == null ? null : user.getId());
    }
}
