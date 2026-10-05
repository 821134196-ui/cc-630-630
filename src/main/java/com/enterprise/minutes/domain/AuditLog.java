package com.enterprise.minutes.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 访问审计：每一次打开正文、下载附件（无论成功/拒绝）都落库。
 * 拒绝事件记录原因，便于事后追溯越权尝试，包括"拿旧链接绕过正文页直接下载"。
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    public Employee employee;

    @Column(nullable = false)
    public String username;

    @ManyToOne(fetch = FetchType.LAZY)
    public Minutes minutes;

    @ManyToOne(fetch = FetchType.LAZY)
    public Attachment attachment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AuditAction action;

    @Column(nullable = false)
    public boolean allowed;

    @Column
    public String reason;

    /** 访问链接中携带的令牌快照，用于核对"旧链接" */
    @Column
    public String linkToken;

    @Column(nullable = false)
    public LocalDateTime at = LocalDateTime.now();
}
