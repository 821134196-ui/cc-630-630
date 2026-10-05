package com.enterprise.minutes.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 历史分发记录：每次发布/重新发布向谁发出了提醒，永久保留，
 * 即使之后转岗或撤回，记录也不删除。
 */
@Entity
@Table(name = "distribution_record")
public class DistributionRecord extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Minutes minutes;

    @Column(nullable = false)
    public int versionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Employee recipient;

    @Column(nullable = false)
    public String recipientEmail;

    /** 对应的模拟邮件日志（提醒中不含正文） */
    @ManyToOne(fetch = FetchType.LAZY)
    public EmailLog emailLog;

    @Column(nullable = false)
    public LocalDateTime sentAt = LocalDateTime.now();
}
