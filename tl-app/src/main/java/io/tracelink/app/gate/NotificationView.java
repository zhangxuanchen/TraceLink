package io.tracelink.app.gate;

/** 缺口提醒收件箱的一条。 */
public record NotificationView(
        Long id,
        Long taskId,
        String taskNo,
        String gateCode,
        String gateName,
        String level,
        String levelLabel,
        int gapDays,
        String title,
        String content,
        boolean read,
        String createdAt) {
}
