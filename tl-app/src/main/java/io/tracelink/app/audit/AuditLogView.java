package io.tracelink.app.audit;

/** 审计行视图：before/after 原样返回给页面，由前端折叠展示 */
public record AuditLogView(
        Long id,
        String action,
        String bizType,
        Long bizId,
        String bizLabel,
        Long actorId,
        String actorName,
        String before,
        String after,
        String traceId,
        String createdAt) {
}
