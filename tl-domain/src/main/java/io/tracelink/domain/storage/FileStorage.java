package io.tracelink.domain.storage;

import java.io.InputStream;
import java.nio.file.Path;

/** 文件卷端口。上传原件与导出件都放这里，数据库只存相对 key */
public interface FileStorage {

    StoredFile store(String category, String fileName, InputStream content);

    Path resolve(String key);

    void delete(String key);

    /** 落盘结果：相对 key、内容哈希与服务端文件名 */
    record StoredFile(String key, String sha256, long size) {
    }
}
