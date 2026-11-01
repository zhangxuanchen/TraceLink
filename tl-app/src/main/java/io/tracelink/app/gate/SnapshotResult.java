package io.tracelink.app.gate;

/** 手动或定时快照的执行结果，用于页面提示与验收断言。 */
public record SnapshotResult(
        String snapDate,
        int taskCount,
        int cellCount,
        int todoCount,
        int notificationCount,
        int prunedDays,
        long durationMs) {
}
