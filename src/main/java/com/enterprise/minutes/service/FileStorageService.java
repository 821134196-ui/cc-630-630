package com.enterprise.minutes.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** 附件本地存储：存放于 data/attachments，随机不可猜测文件名，不对外暴露静态路径。 */
@ApplicationScoped
public class FileStorageService {

    @ConfigProperty(name = "app.data-dir")
    String dataDir;

    Path attachmentsDir;

    @PostConstruct
    void init() {
        attachmentsDir = Paths.get(dataDir, "attachments");
        try {
            Files.createDirectories(attachmentsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建附件目录: " + attachmentsDir, e);
        }
    }

    /** 保存上传文件，返回相对存储路径。 */
    public String store(String originalFilename, byte[] content) {
        String ext = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0 && dot < originalFilename.length() - 1) {
            ext = originalFilename.substring(dot);
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = attachmentsDir.resolve(stored);
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            try {
                Files.createFile(target, PosixFilePermissions.asFileAttribute(perms));
                Files.write(target, content, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (UnsupportedOperationException e) {
                // 非 POSIX 文件系统兜底
                Files.write(target, content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存附件失败: " + originalFilename, e);
        }
        return "attachments/" + stored;
    }

    public Path resolve(String storagePath) {
        // 防路径穿越：只允许 attachments/ 下的单层文件名
        Path base = Paths.get(dataDir).toAbsolutePath().normalize();
        Path p = base.resolve(storagePath).normalize();
        if (!p.startsWith(base.resolve("attachments").normalize()) || p.getParent() == null
                || !p.getParent().equals(base.resolve("attachments").normalize())) {
            throw new SecurityException("非法附件路径: " + storagePath);
        }
        return p;
    }
}
