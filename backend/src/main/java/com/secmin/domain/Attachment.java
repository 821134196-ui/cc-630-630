package com.secmin.domain;

import jakarta.persistence.*;

/**
 * 版本附件。属于某个 {@link MinutesVersion}，与正文同版本原子发布。
 * 文件内容落盘到本地存储目录，数据库只存相对路径；任何读取都必须经过权限校验，
 * 存储目录不通过静态资源对外暴露，无法绕过正文页面直接读文件。
 */
@Entity
@Table(name = "attachment")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
}
