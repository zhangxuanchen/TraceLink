package io.tracelink.domain.importing.model;

import java.time.LocalDateTime;

/** 导入映射模板：同一张表的列名与区块位置签名复用，避免每次重新猜列 */
public class ImportMapping {

    public Long id;
    public String templateSignature;
    public String mapping;
    public Integer hitCount;
    public LocalDateTime updatedAt;
}
