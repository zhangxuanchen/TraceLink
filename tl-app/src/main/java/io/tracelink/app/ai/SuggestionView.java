package io.tracelink.app.ai;

import java.time.LocalDateTime;

/** 建议列表行：payload 与 evidence 已解析为对象 */
public record SuggestionView(
        Long id,
        String type,
        String typeLabel,
        String targetType,
        String targetTypeLabel,
        Long targetId,
        String targetName,
        Object payload,
        Object evidence,
        String status,
        String statusLabel,
        String modelName,
        String promptVer,
        Integer tokenIn,
        Integer tokenOut,
        Integer latencyMs,
        Long acceptedBy,
        String acceptedByName,
        LocalDateTime acceptedAt,
        String acceptResult,
        LocalDateTime createdAt) {
}
