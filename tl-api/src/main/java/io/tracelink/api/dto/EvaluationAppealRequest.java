package io.tracelink.api.dto;

/** 评价申诉入参：必须写清申诉理由 */
public record EvaluationAppealRequest(String reason) {
}
