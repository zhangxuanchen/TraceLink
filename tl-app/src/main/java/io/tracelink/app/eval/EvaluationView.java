package io.tracelink.app.eval;

import java.time.LocalDateTime;

/** 评价列表行：scores 与 evidence 已解析为对象，证据里带工单与追踪记录 ID 供页面下钻 */
public record EvaluationView(
        Long id,
        String targetType,
        String targetTypeLabel,
        Long targetId,
        String targetName,
        String period,
        double score,
        Object scores,
        Object evidence,
        double confidence,
        Integer sampleCnt,
        String status,
        String statusLabel,
        LocalDateTime createdAt) {
}
