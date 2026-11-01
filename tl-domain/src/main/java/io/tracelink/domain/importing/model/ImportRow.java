package io.tracelink.domain.importing.model;

/** 导入行：原始快照 + 解析结果 + 校验意见，确认后回填 task_id 便于追溯 */
public class ImportRow {

    public Long id;
    public Long batchId;
    public String sheet;
    public int rowNo;
    public String raw;
    public String parsed;
    public RowLevel level;
    public String issues;
    public Long taskId;
    public String fingerprint;

    public long levelOrdinal() {
        return level == null ? 0 : level.ordinal();
    }
}
