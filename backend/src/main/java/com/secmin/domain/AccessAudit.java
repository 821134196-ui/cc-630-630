package com.secmin.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 访问审计。每次打开链接、查看正文、下载附件都记录一条；
 * 被拒绝的访问同样记录，便于追溯越权尝试。
 */
@Entity
@Table(name = "access_audit")
public class AccessAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "at_time", nullable = false)
    private Instant at = Instant.now();

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_account")
    private String userAccount;

    @Column(name = "minutes_id")
    private Long minutesId;

    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditResult result;

    /** 拒绝原因（版本撤回 / 转岗密级不足 / 授权已撤销 / 非接收人 / 链接无效 …）。 */
    @Column(length = 200)
    private String reason;

    @Column(length = 64)
    private String token;

    @Column(length = 64)
    private String ip;

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserAccount() { return userAccount; }
    public void setUserAccount(String userAccount) { this.userAccount = userAccount; }
    public Long getMinutesId() { return minutesId; }
    public void setMinutesId(Long minutesId) { this.minutesId = minutesId; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getAttachmentId() { return attachmentId; }
    public void setAttachmentId(Long attachmentId) { this.attachmentId = attachmentId; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public AuditResult getResult() { return result; }
    public void setResult(AuditResult result) { this.result = result; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
}
