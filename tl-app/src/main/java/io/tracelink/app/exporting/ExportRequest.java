package io.tracelink.app.exporting;

import io.tracelink.domain.exporting.model.ExportTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * 导出请求。除模板与日期区间外，还可带上列表页的综合筛选条件：
 * 台账模板按这些条件缩小工单集合，日报模板只认日期区间与汇报人。
 * 状态与类型是用逗号拼起来的多选值，部门走 deptIds 列表。
 */
public record ExportRequest(
        ExportTemplate template,
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
        boolean overdueOnly,
        boolean blockedOnly,
        boolean mine) {

    /** 是否带了综合筛选（任一项生效） */
    public boolean hasTaskFilter() {
        return (keyword != null && !keyword.isBlank()) || status != null || type != null
                || (deptIds != null && !deptIds.isEmpty())
                || overdueOnly || blockedOnly || mine;
    }
}
