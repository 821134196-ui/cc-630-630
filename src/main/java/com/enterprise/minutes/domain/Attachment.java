package com.enterprise.minutes.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 附件。物理文件保存在本地 data/attachments 下，存储名不可猜测；
 * 下载必须经过与正文完全相同的实时权限核验，禁止绕过正文页直接读文件。
 */
@Entity
@Table(name = "attachment")
public class Attachment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public MinutesVersion version;

    @Column(nullable = false)
    public String filename;

    @Column(nullable = false)
    public String contentType;

    @Column(nullable = false)
    public long size;

    /** 本地存储相对路径，不对外暴露 */
    @Column(nullable = false)
    public String storagePath;

    @Column(nullable = false)
    public LocalDateTime uploadedAt = LocalDateTime.now();
}
