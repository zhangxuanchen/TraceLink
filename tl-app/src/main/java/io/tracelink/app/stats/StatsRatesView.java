package io.tracelink.app.stats;

import java.util.List;

/** 统计看板的部门 / 个人维度：每一组都按同一套七项口径重算，列顺序与顶部 KPI 卡一致 */
public record StatsRatesView(
        String from,
        String to,
        List<RateRow> depts,
        List<RateRow> persons) {

    /** tasks 是这一组的工单数，用来判断样本量；kpis 与顶部七项同序同口径 */
    public record RateRow(Long id, String name, String deptName, Long parentId, int tasks, List<KpiView> kpis) {
    }
}
