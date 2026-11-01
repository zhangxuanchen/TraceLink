package io.tracelink.domain.exporting.model;

import java.time.LocalDateTime;

/** 导出任务：同步生成，但保留任务记录以便审计、重下与失败原因追溯 */
public class ExportTask {

    public Long id;
    public ExportTemplate templateCode;
    public String params;
    public ExportStatus status;
    public String fileKey;
    public Integer rowCnt;
    public String errorMsg;
    public Long createdBy;
    public LocalDateTime createdAt;
}
