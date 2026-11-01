package io.tracelink.app.ai;

/** 采纳结果：执行确定性动作后返回被改动的对象，便于页面跳转与审计对齐 */
public record SuggestionAcceptView(
        Long id,
        String status,
        String resultType,
        String objectType,
        Long objectId,
        String objectNo,
        String message,
        java.time.LocalDateTime acceptedAt) {
}
