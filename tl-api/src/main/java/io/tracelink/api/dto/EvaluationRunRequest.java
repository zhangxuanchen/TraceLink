package io.tracelink.api.dto;

/** 评价生成入参（EVL-1）：周期默认当月，对象为空时人员与任务都跑 */
public record EvaluationRunRequest(String period, String targetType) {
}
