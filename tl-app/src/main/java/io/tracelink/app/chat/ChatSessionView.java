package io.tracelink.app.chat;

import java.time.LocalDateTime;

/** 会话列表行 */
public record ChatSessionView(
        Long id,
        String title,
        String scopeType,
        String scopeTypeLabel,
        Long scopeId,
        Integer msgCount,
        LocalDateTime lastMsgAt,
        String status,
        String statusLabel,
        LocalDateTime createdAt) {
}
