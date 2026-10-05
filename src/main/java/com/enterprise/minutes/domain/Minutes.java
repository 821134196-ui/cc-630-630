package com.enterprise.minutes.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 一份会议纪要（逻辑文档），每次修订产生一个不可拆分的新版本。 */
@Entity
@Table(name = "minutes")
public class Minutes extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String title;

    /** 密级：在编辑页、查看页、审批页显著展示 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Classification classification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public MinutesStatus status = MinutesStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Employee createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    public Employee approver;

    /** 当前发布版本（正文+附件作为同一整体发布）；草稿时可能只有草稿版本 */
    @ManyToOne(fetch = FetchType.LAZY)
    public MinutesVersion currentVersion;

    /** 当前草稿版本（提交审批前记录员正在编辑的版本） */
    @ManyToOne(fetch = FetchType.LAZY)
    public MinutesVersion draftVersion;

    @Column
    public String rejectionComment;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "minutes", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNo DESC")
    public List<MinutesVersion> versions = new ArrayList<>();

    public void touch() {
        updatedAt = LocalDateTime.now();
    }
}
