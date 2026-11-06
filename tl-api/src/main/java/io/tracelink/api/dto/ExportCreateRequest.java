package io.tracelink.api.dto;

import java.time.LocalDate;
import java.util.List;

/** 导出请求：模板编码 + 时间范围 + 列表页那套综合筛选（关键词、状态、类型、部门、逾期、受阻、与我相关） */
public record ExportCreateRequest(
        String template,
        LocalDate from,
        LocalDate to,
        Long deptId,
        List<Long> deptIds,
        Long employeeId,
        LocalDate reportDate,
        String remark,
        String keyword,
        String status,
        String type,
        Boolean overdueOnly,
        Boolean blockedOnly,
        Boolean mine) {
}
