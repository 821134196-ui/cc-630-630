package com.secmin.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 分发授权（历史分发记录）。审批通过时为每位最终接收人生成一条，
 * 并通过模拟邮件把带 token 的提醒链接发出。
 *
 * 关键：记录永久保留，不做物理删除。审批撤回 / 转岗 / 授权撤销后，
 * 记录仍在，但访问入口每次实时核验 {@link MinutesVersion#getState()}、
 * 接收人快照与员工当前岗位密级，不通过即拒绝——旧链接立即失效。
 */
@Entity
@Table(name = "distribution_grant",
        uniqueConstraints = @UniqueConstraint(name = "uk_grant_token", columnNames = "token"))
public class DistributionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** 管理员是否已撤销该员工的这一条定向授权（撤销后立即失效）。 */
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_access_at")
    private Instant lastAccessAt;

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getMinutesId() { return minutesId; }
    public void setMinutesId(Long minutesId) { this.minutesId = minutesId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getLastAccessAt() { return lastAccessAt; }
    public void setLastAccessAt(Instant lastAccessAt) { this.lastAccessAt = lastAccessAt; }
}
