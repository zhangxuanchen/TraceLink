package io.tracelink.app.chat;

import java.time.LocalDateTime;

/** 会话历史行：citations 与 toolCalls 已解析为对象，页面直接渲染 */
public record ChatMessageView(
        Long id,
        String role,
        String roleLabel,
        String content,
        Object citations,
        Object toolCalls,
        String modelName,
        String promptVer,
        Integer tokenIn,
        Integer tokenOut,
        Integer latencyMs,
        LocalDateTime createdAt) {
}
