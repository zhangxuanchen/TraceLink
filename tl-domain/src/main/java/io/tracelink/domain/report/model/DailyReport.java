package io.tracelink.domain.report.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 日报页：一次汇报一页，(日期, 汇报人) 唯一，raw 保留页级原始快照 */
public class DailyReport {

    public Long id;
    public LocalDate reportDate;
    public Long reporterId;
    public String sourceSheet;
    public Long importBatchId;
    public String raw;
    public LocalDateTime createdAt;
}
