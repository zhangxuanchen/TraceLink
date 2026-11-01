package io.tracelink.app.stats;

/** 逾期前十用的轻量引用，点开即可进详情 */
public record TaskRefView(
        Long id,
        String taskNo,
        String title,
        String deptName,
        String assigneeName,
        String statusLabel,
        int overdueDays,
        String planEnd) {
}
