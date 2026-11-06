package io.tracelink.app.desk;

/** 工作台卡片：只带打卡与验收要用的字段，点开进工单详情 */
public record TaskCard(
        Long id,
        String taskNo,
        String title,
        String status,
        String statusLabel,
        String planEnd,
        boolean overdue,
        int overdueDays,
        int progress,
        int rejectCount,
        String primaryAssigneeName,
        String ownerName) {
}
