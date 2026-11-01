package io.tracelink.api.dto;

import java.time.LocalDate;

/** 导出请求：模板编码 + 时间范围 */
public record ExportCreateRequest(
        String template,
        LocalDate from,
        LocalDate to,
        Long deptId,
        Long employeeId,
        LocalDate reportDate,
        String remark) {
}
