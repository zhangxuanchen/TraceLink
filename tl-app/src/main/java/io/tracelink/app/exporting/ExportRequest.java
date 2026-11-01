package io.tracelink.app.exporting;

import io.tracelink.domain.exporting.model.ExportTemplate;

import java.time.LocalDate;

/** 导出请求：模板 + 时间范围；DAILY 模板只看 reportDate */
public record ExportRequest(
        ExportTemplate template,
        LocalDate from,
        LocalDate to,
        Long deptId,
        Long employeeId,
        LocalDate reportDate,
        String remark) {
}
