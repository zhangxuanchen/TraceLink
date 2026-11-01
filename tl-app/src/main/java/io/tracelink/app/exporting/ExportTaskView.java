package io.tracelink.app.exporting;

/** 导出任务视图 */
public record ExportTaskView(
        Long id,
        String templateCode,
        String templateLabel,
        String status,
        String statusLabel,
        String fileName,
        Integer rowCnt,
        String createdAt,
        String errorMsg) {
}
