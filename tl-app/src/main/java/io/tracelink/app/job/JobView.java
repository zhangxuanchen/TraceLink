package io.tracelink.app.job;

/** 任务视图：页面轮询拿 id、status、progress 与结果摘要 */
public record JobView(
        Long id,
        String jobType,
        String jobTypeLabel,
        String status,
        String statusLabel,
        String payload,
        int progress,
        int attempts,
        String lastError,
        String resultText,
        Long createdBy,
        String createdByName,
        String createdAt,
        String updatedAt) {
}
