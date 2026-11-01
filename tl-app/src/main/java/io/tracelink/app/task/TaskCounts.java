package io.tracelink.app.task;

/** 看板计数，按当前调用人的数据范围 */
public record TaskCounts(
        long pending,
        long inProgress,
        long blocked,
        long submitted,
        long done,
        long overdue,
        long mine) {
}
