package io.tracelink.domain.importing.model;

import java.time.LocalDateTime;

/** 导入批次，一次上传一份文件；file_hash 唯一，重复上传直接命中旧批次 */
public class ImportBatch {

    public Long id;
    public String fileHash;
    public String fileName;
    public String fileKey;
    public String mode;
    public ImportStatus status;
    public Integer sheetTotal;
    public Integer rowTotal;
    public Integer rowOk;
    public Integer rowWarn;
    public Integer rowError;
    /** 识别出的模板签名与列映射，JSON 原文，供预览页展示与模板复用 */
    public String mapping;
    public String errorMsg;
    public Long createdBy;
    public LocalDateTime createdAt;

    public int rowCount() {
        return rowTotal == null ? 0 : rowTotal;
    }
}
