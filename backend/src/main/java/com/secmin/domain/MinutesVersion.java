package com.secmin.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 纪要版本。一次修订 = 正文 + 全部附件作为同一版本原子发布，
 * 不允许只改正文或只换附件。
 *
 * 密级标注在版本上（修订时允许调整密级），访问控制以版本密级为准。
 */
@Entity
@Table(name = "minutes_version")
public class MinutesVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 20)
    private Classification classification;

    @Lob
    @Column(name = "body_text", nullable = false)
    private String bodyText;

    @Column(name = "change_note")
    private String changeNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private VersionState state = VersionState.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "submitted_by", nullable = false)
    private Long submittedById;

    /** 审批/撤回人。 */
    @Column(name = "reviewed_by")
    private Long reviewedById;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** 审批通过时冻结的最终接收名单快照。 */
    @Column(name = "approved_recipient_ids")
    private String approvedRecipientIds;

    public Long getId() { return id; }
    public Long getMinutesId() { return minutesId; }
    public void setMinutesId(Long minutesId) { this.minutesId = minutesId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public Classification getClassification() { return classification; }
    public void setClassification(Classification classification) { this.classification = classification; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
    public VersionState getState() { return state; }
    public void setState(VersionState state) { this.state = state; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getSubmittedById() { return submittedById; }
    public void setSubmittedById(Long submittedById) { this.submittedById = submittedById; }
    public Long getReviewedById() { return reviewedById; }
    public void setReviewedById(Long reviewedById) { this.reviewedById = reviewedById; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getApprovedRecipientIds() { return approvedRecipientIds; }
    public void setApprovedRecipientIds(String approvedRecipientIds) { this.approvedRecipientIds = approvedRecipientIds; }
}
