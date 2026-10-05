package com.enterprise.minutes.service;

import com.enterprise.minutes.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * 权限核验中心——本系统的安全核心。
 *
 * 铁律：员工【每一次】打开链接/下载附件都在这里按【当前】岗位与授权实时计算，
 * 不使用任何缓存、不信任旧令牌、不信任"邮件收到过"。
 * 因此转岗或授权撤回后，无需通知，旧链接在下一次请求时立即失效。
 *
 * 注意：本类只在短事务内计算结论，不写审计——拒绝时外层请求会回滚，
 * 审计由资源层在检查事务提交后的独立事务中落库，确保拒绝记录不被回滚。
 */
@ApplicationScoped
public class AccessService {

    /** 正文访问核验结论 */
    public record ViewResult(AccessDecision decision, Long minutesId) {
    }

    /** 附件下载核验结论 + 流式输出所需的文件元数据（均为基本类型，事务外安全使用） */
    public record DownloadResult(AccessDecision decision, Long minutesId, Long attachmentId,
                                 String filename, String contentType, String storagePath) {
    }

    @Transactional
    public ViewResult evaluateView(Employee employee, Long minutesId, Long tokenEmployeeId) {
        Minutes minutes = Minutes.findById(minutesId);
        return new ViewResult(doCheck(employee, minutes, tokenEmployeeId), minutesId);
    }

    @Transactional
    public DownloadResult evaluateDownload(Employee employee, Long attachmentId, Long tokenEmployeeId) {
        Attachment attachment = Attachment.findById(attachmentId);
        if (attachment == null) {
            return new DownloadResult(AccessDecision.deny("附件不存在"), null, attachmentId,
                    null, null, null);
        }
        MinutesVersion version = attachment.version;
        Minutes minutes = version.minutes;
        AccessDecision d = doCheck(employee, minutes, tokenEmployeeId);
        if (d.allowed()) {
            Long currentVersionId = minutes.currentVersion != null ? minutes.currentVersion.id : null;
            if (currentVersionId == null || !currentVersionId.equals(version.id)) {
                // 附件属于已被取代的旧版本 → 不允许通过当前链接读取
                d = AccessDecision.deny("附件不属于当前发布版本（正文与附件版本不一致的请求被拒绝）");
            }
        }
        return new DownloadResult(d, minutes.id, attachment.id,
                attachment.filename, attachment.contentType, attachment.storagePath);
    }

    /** 在事务内加载当前发布版本的全部展示数据（转为基本类型 DTO，事务结束后使用安全）。 */
    @Transactional
    public BodyData loadBodyData(Long minutesId) {
        Minutes minutes = Minutes.findById(minutesId);
        if (minutes == null || minutes.currentVersion == null) {
            return null;
        }
        MinutesVersion v = minutes.currentVersion;
        return new BodyData(
                minutes.id, minutes.title, minutes.classification.name(),
                minutes.classification.getLabel(), v.versionNo, v.body, v.publishedAt,
                v.attachments.stream().map(a -> new AttachmentData(
                        a.id, a.filename, a.contentType, a.size, v.versionNo)).toList());
    }

    public record AttachmentData(Long id, String filename, String contentType, long size, int versionNo) {
    }

    public record BodyData(Long minutesId, String title, String classification,
                           String classificationLabel, int versionNo, String body,
                           java.time.LocalDateTime publishedAt,
                           java.util.List<AttachmentData> attachments) {
    }

    private AccessDecision doCheck(Employee employee, Minutes minutes, Long tokenEmployeeId) {
        if (employee == null) {
            return AccessDecision.deny("用户不存在");
        }
        if (!employee.active) {
            return AccessDecision.deny("账号已停用");
        }
        if (minutes == null || minutes.status != MinutesStatus.APPROVED) {
            return AccessDecision.deny("纪要不存在或尚未发布");
        }
        if (tokenEmployeeId != null && !tokenEmployeeId.equals(employee.id)) {
            return AccessDecision.deny("链接与当前登录员工不匹配（转发链接无效）");
        }
        Grant grant = Grant.findActive(minutes, employee);
        if (grant == null) {
            return AccessDecision.deny("未在该纪要的授权名单中（可能已转岗或授权被撤回）");
        }
        // 纵深防御：授权是发给"原岗位上的你"的；岗位/部门已变化即拒绝
        if (!grant.grantedPosition.equals(employee.position)
                || !grant.grantedDepartment.equals(employee.department)) {
            return AccessDecision.deny("岗位已变动，原岗位授权不再适用");
        }
        // 实时读取当前密级资质，而非授权时的快照
        if (employee.clearanceLevel < minutes.classification.getLevel()) {
            return AccessDecision.deny("当前岗位密级资质不足（当前 " + employee.clearanceLevel
                    + " < 要求 " + minutes.classification.getLevel() + "）");
        }
        return AccessDecision.ok();
    }
}
