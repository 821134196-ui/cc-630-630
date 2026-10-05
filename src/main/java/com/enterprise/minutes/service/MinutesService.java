package com.enterprise.minutes.service;

import com.enterprise.minutes.domain.*;
import com.enterprise.minutes.security.LinkTokens;
import com.enterprise.minutes.web.MinutesForm;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 纪要业务核心：
 * - 创建/修订：正文与附件在【同一请求、同一事务、同一版本】落库；
 * - 审批：负责人确认最终名单后整体发布当前草稿版本，发提醒邮件并写分发历史；
 * - 撤回/转岗：只改授权状态与员工岗位，访问资格由 {@link AccessService} 每次实时计算。
 */
@ApplicationScoped
public class MinutesService {

    @Inject
    FileStorageService storage;

    @Inject
    MailService mailService;

    @Inject
    AuditService auditService;

    @Inject
    LinkTokens linkTokens;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.public-base-url",
            defaultValue = "http://localhost:8080")
    String publicBaseUrl;

    /* ============================ 起草 / 修订 ============================ */

    @Transactional
    public Minutes createDraft(Employee recorder, MinutesForm form) {
        validateForm(form);
        Minutes m = new Minutes();
        m.title = form.title.trim();
        m.classification = Classification.valueOf(form.classification);
        m.createdBy = recorder;
        m.draftVersion = buildVersion(m, 1, recorder, form);
        m.versions.add(m.draftVersion);
        m.persist();
        return m;
    }

    /**
     * 修订：基于当前已发布版本创建新草稿版本。新版本号 = 发布版本号 + 1，
     * 新正文与新附件在同一事务写入新版本；旧发布版本在新版本获批前继续有效。
     */
    @Transactional
    public Minutes revise(Long minutesId, Employee recorder, MinutesForm form) {
        validateForm(form);
        Minutes m = getMinutes(minutesId);
        if (m.status == MinutesStatus.PENDING) {
            throw new BadRequestException("该纪要正在审批中，不能修订");
        }
        int baseNo = m.currentVersion != null ? m.currentVersion.versionNo
                : (m.draftVersion != null ? m.draftVersion.versionNo : 0);
        // 清理旧草稿版本（被驳回后再次编辑）
        if (m.draftVersion != null && (m.currentVersion == null
                || !m.draftVersion.id.equals(m.currentVersion.id))) {
            m.versions.remove(m.draftVersion);
            m.draftVersion = null;
        }
        MinutesVersion v = buildVersion(m, baseNo + 1, recorder, form);
        if (form.classification != null && !form.classification.isBlank()) {
            m.classification = Classification.valueOf(form.classification);
        }
        m.title = form.title.trim();
        m.draftVersion = v;
        m.versions.add(v);
        m.status = MinutesStatus.DRAFT;
        m.rejectionComment = null;
        m.touch();
        return m;
    }

    private void validateForm(MinutesForm form) {
        if (form == null || form.title == null || form.title.isBlank()) {
            throw new BadRequestException("标题不能为空");
        }
        if (form.body == null || form.body.isBlank()) {
            throw new BadRequestException("正文不能为空");
        }
        if (form.classification == null || form.classification.isBlank()) {
            throw new BadRequestException("密级不能为空");
        }
        try {
            Classification.valueOf(form.classification);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("非法密级: " + form.classification);
        }
        if (form.files == null || form.files.isEmpty()) {
            // 保证"一个版本 = 正文 + 附件"的不变量，便于审计与一致性校验
            throw new BadRequestException("每个版本必须包含至少一个附件（正文与附件作为同一版本提交）");
        }
    }

    private MinutesVersion buildVersion(Minutes m, int versionNo, Employee recorder, MinutesForm form) {
        MinutesVersion v = new MinutesVersion();
        v.minutes = m;
        v.versionNo = versionNo;
        v.body = form.body;
        v.createdBy = recorder;
        for (var upload : form.files) {
            try {
                byte[] content = java.nio.file.Files.readAllBytes(upload.uploadedFile());
                Attachment a = new Attachment();
                a.version = v;
                a.filename = upload.fileName();
                a.contentType = upload.contentType() != null ? upload.contentType()
                        : "application/octet-stream";
                a.size = content.length;
                a.storagePath = storage.store(upload.fileName(), content);
                v.attachments.add(a);
            } catch (java.io.IOException e) {
                throw new BadRequestException("读取上传附件失败: " + upload.fileName());
            }
        }
        return v;
    }

    /* ============================ 提交审批 ============================ */

    @Transactional
    public void submitForApproval(Long minutesId, Employee recorder, List<String> proposedUsernames) {
        Minutes m = getMinutes(minutesId);
        if (m.status == MinutesStatus.PENDING) {
            throw new BadRequestException("已在审批中");
        }
        if (m.draftVersion == null) {
            throw new BadRequestException("没有可提交的草稿版本");
        }
        if (proposedUsernames == null || proposedUsernames.isEmpty()) {
            throw new BadRequestException("候选接收名单不能为空");
        }
        ProposedRecipient.delete("minutes", m);
        Set<String> seen = new LinkedHashSet<>(proposedUsernames);
        for (String username : seen) {
            Employee e = Employee.findByUsername(username);
            if (e == null || !e.active) {
                throw new BadRequestException("接收人不存在或已停用: " + username);
            }
            if (e.clearanceLevel < m.classification.getLevel()) {
                throw new BadRequestException(
                        "接收人 %s 密级资质不足（%d < %d），不可列入名单"
                                .formatted(username, e.clearanceLevel, m.classification.getLevel()));
            }
            new ProposedRecipient(m, e).persist();
        }
        m.status = MinutesStatus.PENDING;
        m.touch();
    }

    /* ============================ 审批发布 ============================ */

    /**
     * 负责人审批：finalUsernames 为预览确认后的最终接收名单。
     * 发布动作一气呵成：版本发布 + 授权同步 + 提醒邮件 + 历史分发记录。
     */
    @Transactional
    public void approve(Long minutesId, Employee approver, List<String> finalUsernames) {
        Minutes m = getMinutes(minutesId);
        if (m.status != MinutesStatus.PENDING) {
            throw new BadRequestException("仅待审批纪要可批准");
        }
        if (finalUsernames == null || finalUsernames.isEmpty()) {
            throw new BadRequestException("最终接收名单不能为空");
        }
        Set<Employee> finalRecipients = new LinkedHashSet<>();
        for (String username : new LinkedHashSet<>(finalUsernames)) {
            Employee e = Employee.findByUsername(username);
            if (e == null || !e.active) {
                throw new BadRequestException("接收人不存在或已停用: " + username);
            }
            if (e.clearanceLevel < m.classification.getLevel()) {
                throw new BadRequestException(
                        "接收人 %s 当前密级资质不足，不能出现在最终名单中".formatted(username));
            }
            finalRecipients.add(e);
        }

        MinutesVersion publishing = m.draftVersion;
        publishing.published = true;
        publishing.publishedAt = LocalDateTime.now();

        // 1) 同步授权：名单外的现行授权立即撤回（历史行保留）；
        //    历史上被撤回、本次又重回名单的员工，复用原授权行并重新激活（保留完整轨迹）。
        List<Grant> allGrants = Grant.find("minutes", m).list();
        Map<Long, Grant> byEmployee = new HashMap<>();
        for (Grant g : allGrants) {
            byEmployee.put(g.employee.id, g);
        }
        for (Grant g : new ArrayList<>(byEmployee.values())) {
            boolean stillIn = finalRecipients.stream().anyMatch(e -> e.id.equals(g.employee.id));
            if (!stillIn && g.active) {
                g.active = false;
                g.revokedAt = LocalDateTime.now();
                g.revokeReason = RevokeReason.REMOVED_BY_APPROVER;
            }
        }
        // 2) 新增或重新激活授权（按当前岗位快照）
        for (Employee e : finalRecipients) {
            boolean isNew = !byEmployee.containsKey(e.id);
            Grant g = byEmployee.get(e.id);
            if (isNew) {
                g = new Grant();
                g.minutes = m;
                g.employee = e;
                g.grantedBy = approver;
                g.createdAt = LocalDateTime.now();
            }
            g.active = true;
            g.revokedAt = null;
            g.revokeReason = null;
            g.grantedDepartment = e.department;
            g.grantedPosition = e.position;
            g.grantedClearanceLevel = e.clearanceLevel;
            if (isNew) {
                // IDENTITY 主键在 persist 时立即 INSERT，必须先填齐非空字段
                g.persist();
            }
        }

        // 3) 发布：正文与附件作为同一版本生效
        m.currentVersion = publishing;
        m.draftVersion = null;
        m.status = MinutesStatus.APPROVED;
        m.approver = approver;
        m.touch();
        ProposedRecipient.delete("minutes", m);

        // 4) 定向分发：每人一封提醒邮件（不含正文）+ 永久分发记录
        long now = System.currentTimeMillis() / 1000;
        for (Employee e : finalRecipients) {
            String token = linkTokens.issue(m.id, e.id, now);
            String link = publicBaseUrl + "/#/view/" + m.id + "?t=" + token;
            EmailLog mail = mailService.sendNotification(
                    e.email, e.name, m.title,
                    new MailService.ClassificationInfo(m.classification.getLabel()),
                    link, publishing.versionNo);
            DistributionRecord dr = new DistributionRecord();
            dr.minutes = m;
            dr.versionNo = publishing.versionNo;
            dr.recipient = e;
            dr.recipientEmail = e.email;
            dr.emailLog = mail;
            dr.persist();
        }

        auditService.log(approver, m.id, null, AuditAction.APPROVE, true,
                "发布 v%d，接收 %d 人".formatted(publishing.versionNo, finalRecipients.size()),
                null);
    }

    @Transactional
    public void reject(Long minutesId, Employee approver, String comment) {
        Minutes m = getMinutes(minutesId);
        if (m.status != MinutesStatus.PENDING) {
            throw new BadRequestException("仅待审批纪要可驳回");
        }
        m.status = MinutesStatus.REJECTED;
        m.rejectionComment = comment;
        m.approver = approver;
        m.touch();
        ProposedRecipient.delete("minutes", m);
        auditService.log(approver, m.id, null, AuditAction.REJECT, true, comment, null);
    }

    /* ============================ 授权撤回 / 转岗 ============================ */

    @Transactional
    public void revokeRecipient(Long minutesId, String username, Employee approver) {
        Minutes m = getMinutes(minutesId);
        Employee e = Employee.findByUsername(username);
        if (e == null) {
            throw new NotFoundException("员工不存在: " + username);
        }
        Grant g = Grant.findActive(m, e);
        if (g == null) {
            throw new NotFoundException("该员工不在当前授权名单中");
        }
        g.active = false;
        g.revokedAt = LocalDateTime.now();
        g.revokeReason = RevokeReason.REMOVED_BY_APPROVER;
        auditService.log(approver, m.id, null, AuditAction.REVOKE_RECIPIENT, true,
                "撤回对 " + username + " 的授权", null);
    }

    /**
     * 负责人向已发布纪要追加/恢复一名接收人（例如员工转岗后又调回）。
     * 复用既有授权行并重新激活，按当前岗位快照更新；补发一封提醒邮件并留分发记录。
     */
    @Transactional
    public void grantRecipient(Long minutesId, String username, Employee approver) {
        Minutes m = getMinutes(minutesId);
        if (m.status != MinutesStatus.APPROVED || m.currentVersion == null) {
            throw new BadRequestException("仅已发布纪要可追加接收人");
        }
        Employee e = Employee.findByUsername(username);
        if (e == null || !e.active) {
            throw new NotFoundException("员工不存在或已停用: " + username);
        }
        if (e.clearanceLevel < m.classification.getLevel()) {
            throw new BadRequestException(
                    "接收人 %s 当前密级资质不足，不能加入名单".formatted(username));
        }
        Grant g = Grant.find("minutes = ?1 and employee = ?2", m, e).firstResult();
        boolean isNew = g == null;
        if (isNew) {
            g = new Grant();
            g.minutes = m;
            g.employee = e;
            g.createdAt = LocalDateTime.now();
        }
        g.active = true;
        g.revokedAt = null;
        g.revokeReason = null;
        g.grantedBy = approver;
        g.grantedDepartment = e.department;
        g.grantedPosition = e.position;
        g.grantedClearanceLevel = e.clearanceLevel;
        if (isNew) {
            g.persist();
        }

        MinutesVersion v = m.currentVersion;
        String token = linkTokens.issue(m.id, e.id, System.currentTimeMillis() / 1000);
        String link = publicBaseUrl + "/#/view/" + m.id + "?t=" + token;
        EmailLog mail = mailService.sendNotification(
                e.email, e.name, m.title,
                new MailService.ClassificationInfo(m.classification.getLabel()),
                link, v.versionNo);
        DistributionRecord dr = new DistributionRecord();
        dr.minutes = m;
        dr.versionNo = v.versionNo;
        dr.recipient = e;
        dr.recipientEmail = e.email;
        dr.emailLog = mail;
        dr.persist();

        auditService.log(approver, m.id, null, AuditAction.APPROVE, true,
                "追加/恢复接收人 " + username, null);
    }

    /**
     * 人事调动/资质调整：更新当前岗位信息，并即时作废相关授权——
     * 旧链接在下一次打开时被 AccessService 拒绝。历史授权与分发记录保留。
     */
    @Transactional
    public void transfer(Employee admin, String username, String newDepartment,
                         String newPosition, int newClearance) {
        Employee e = Employee.findByUsername(username);
        if (e == null) {
            throw new NotFoundException("员工不存在: " + username);
        }
        boolean positionChanged = !Objects.equals(e.department, newDepartment)
                || !Objects.equals(e.position, newPosition);
        e.department = newDepartment;
        e.position = newPosition;
        e.clearanceLevel = newClearance;

        List<Grant> grants = Grant.find("employee", e).list();
        for (Grant g : grants) {
            if (!g.active) {
                continue;
            }
            if (positionChanged) {
                g.active = false;
                g.revokedAt = LocalDateTime.now();
                g.revokeReason = RevokeReason.TRANSFERRED;
                auditService.log(admin, g.minutes.id, null, AuditAction.TRANSFER, true,
                        username + " 转岗至 " + newDepartment + "/" + newPosition, null);
            } else if (g.minutes.classification.getLevel() > newClearance) {
                g.active = false;
                g.revokedAt = LocalDateTime.now();
                g.revokeReason = RevokeReason.CLEARANCE_DOWNGRADE;
                auditService.log(admin, g.minutes.id, null, AuditAction.TRANSFER, true,
                        username + " 密级资质下调至 " + newClearance, null);
            }
        }
    }

    private Minutes getMinutes(Long id) {
        Minutes m = Minutes.findById(id);
        if (m == null) {
            throw new NotFoundException("纪要不存在: " + id);
        }
        return m;
    }
}
