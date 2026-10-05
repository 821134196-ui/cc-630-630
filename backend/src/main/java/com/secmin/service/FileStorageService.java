package com.secmin.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * 本地附件存储。文件放在 data/files 下，不通过任何静态资源路径暴露，
 * 外部只能走 /api/access/attachment/** 且必须通过权限校验。
 */
@ApplicationScoped
public class FileStorageService {

    private final Path rootDir;

    public FileStorageService(@ConfigProperty(name = "app.data-dir") String dataDir) {
        this.rootDir = Paths.get(dataDir, "files");
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 保存一个版本的附件。storageKey 用一次版本提交共享的随机批次目录，
     * 与数据库记录在同一事务中写入；失败时调用 {@link #discardBatch} 清理。
     */
    public String store(String batchId, String safeName, byte[] content) {
        try {
            Path batchDir = rootDir.resolve(batchId);
            Files.createDirectories(batchDir);
            String unique = UUID.randomUUID() + "_" + sanitize(safeName);
            Path target = batchDir.resolve(unique);
            Files.write(target, content,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return batchId + "/" + unique;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] read(String storagePath) {
        // 规范化并校验路径必须位于 rootDir 之下，防路径穿越
        Path resolved = rootDir.resolve(storagePath).toAbsolutePath().normalize();
        if (!resolved.startsWith(rootDir.toAbsolutePath().normalize())) {
            throw new SecurityException("非法附件路径: " + storagePath);
        }
        try {
            return Files.readAllBytes(resolved);
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("附件文件不存在", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void discardBatch(String batchId) {
        Path batchDir = rootDir.resolve(batchId).toAbsolutePath().normalize();
        if (!batchDir.startsWith(rootDir.toAbsolutePath().normalize())) {
            return;
        }
        try {
            if (!Files.exists(batchDir)) {
                return;
            }
            try (var walk = Files.walk(batchDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                        });
            }
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }

    private String sanitize(String name) {
        String base = name == null ? "file" : Paths.get(name).getFileName().toString();
        return base.replaceAll("[\\\\/]+", "_").replaceAll("[^a-zA-Z0-9.\\-_\\u4e00-\\u9fa5]", "_");
    }
}
