package com.enterprise.minutes.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 模拟邮件。只发送提醒（标题、密级、链接），绝不携带敏感正文与附件。
 * 邮件落库供"外发箱"页面查看，验证邮件内容是否合规。
 */
@Entity
@Table(name = "email_log")
public class EmailLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String toEmail;

    @Column(nullable = false)
    public String subject;

    @Lob
    @Column(nullable = false)
    public String body;

    @Column(nullable = false)
    public LocalDateTime sentAt = LocalDateTime.now();
}
