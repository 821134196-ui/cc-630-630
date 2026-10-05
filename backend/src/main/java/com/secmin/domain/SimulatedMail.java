package com.secmin.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 模拟邮件。不接真实 SMTP，落库 + 落盘一份 .eml 形式文本。
 * 安全要求：邮件只发提醒和带 token 的访问链接，绝不包含纪要正文/附件内容。
 */
@Entity
@Table(name = "simulated_mail")
public class SimulatedMail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "to_account", nullable = false)
    private String toAccount;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    @Column(nullable = false)
    private String subject;

    @Lob
    @Column(name = "body_text", nullable = false)
    private String bodyText;

    @Column(name = "minutes_id", nullable = false)
    private Long minutesId;

    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "classification", length = 20)
    private String classification;

    /** 落盘副本路径。 */
    @Column(name = "file_path")
    private String filePath;

    public Long getId() { return id; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public String getToAccount() { return toAccount; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount; }
    public String getToEmail() { return toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public Long getMinutesId() { return minutesId; }
    public void setMinutesId(Long minutesId) { this.minutesId = minutesId; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
