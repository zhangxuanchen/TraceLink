package io.tracelink.infra.storage;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 本地文件卷：root/类别/哈希-文件名。幂等键直接用内容哈希，同一文件天然同一条路径 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${tracelink.storage.root:./data/files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String category, String fileName, InputStream content) {
        String safeName = sanitize(fileName);
        try {
            Files.createDirectories(root.resolve(category));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path temp = Files.createTempFile(root.resolve(category), "upload-", ".tmp");
            try (InputStream in = content;
                 OutputStream out = new DigestOutputStream(
                         Files.newOutputStream(temp, StandardOpenOption.WRITE), digest)) {
                in.transferTo(out);
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            String key = category + "/" + hash + "-" + safeName;
            Path target = root.resolve(key);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(key, hash, Files.size(target));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件写入失败：" + e.getMessage());
        }
    }

    @Override
    public Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在或已清理：" + key);
        }
        return path;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException ignored) {
            // 清理失败不影响主流程，导出保留期任务会再兜底
        }
    }

    private String sanitize(String fileName) {
        String base = fileName == null || fileName.isBlank() ? "file" : fileName;
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }
}
