package com.secmin.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 会议纪要（逻辑文档）。每次修订新增一条 {@link MinutesVersion}。
 */
@Entity
@Table(name = "minutes")
public class Minutes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private Long createdById;

    /** 当前最新版本号，从 1 开始。 */
    @Column(name = "current_version_no", nullable = false)
    private int currentVersionNo = 0;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long createdById) { this.createdById = createdById; }
    public int getCurrentVersionNo() { return currentVersionNo; }
    public void setCurrentVersionNo(int currentVersionNo) { this.currentVersionNo = currentVersionNo; }
}
