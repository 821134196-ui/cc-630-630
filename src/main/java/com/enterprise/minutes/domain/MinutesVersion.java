package com.enterprise.minutes.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 纪要版本。正文与附件挂在同一个版本下，随版本一起创建、一起发布：
 * 不存在"只改正文"或"只换附件"的部分更新。
 */
@Entity
@Table(name = "minutes_version")
public class MinutesVersion extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Minutes minutes;

    @Column(nullable = false)
    public int versionNo;

    /** 正文（敏感内容，只通过受控接口返回） */
    @Lob
    @Column(nullable = false)
    public String body;

    /** 该版本的附件与正文同生共死 */
    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Attachment> attachments = new ArrayList<>();

    /** 草稿 / 已发布 */
    @Column(nullable = false)
    public boolean published = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Employee createdBy;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public LocalDateTime publishedAt;
}
