package io.tracelink.api.dto;

/** 预览页行校正请求，字段与 RowEditCommand 对齐 */
public record ImportRowEditRequest(
        String title,
        String assigneeId,
        String deptId,
        String planEnd,
        String action,
        String resultText,
        String problemText,
        String solutionText) {
}
