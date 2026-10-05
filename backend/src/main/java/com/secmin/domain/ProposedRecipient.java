package com.secmin.domain;

import jakarta.persistence.*;

/**
 * 记录员提交版本时建议的接收人名单，供负责人预览、增删后形成最终名单。
 * 审批后最终名单冻结在 {@link MinutesVersion#getApprovedRecipientIds()}，
 * 本表仅保留为提交痕迹。
 */
@Entity
@Table(name = "proposed_recipient",
        uniqueConstraints = @UniqueConstraint(name = "uk_proposed",
                columnNames = {"version_id", "user_id"}))
public class ProposedRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
