package io.tracelink.app.stats;

import java.util.List;

/** 驾驶舱一次性取全：KPI 七项与管理者图表数据 */
public record StatsOverviewView(
        String from,
        String to,
        String kpiVersion,
        List<KpiView> kpis,
        List<TrendPoint> trend,
        List<DeptStat> depts,
        List<Slice> status,
        List<Slice> overdueBins,
        List<TaskRefView> overdueTop,
        List<Slice> recurrent,
        List<Slice> types,
        List<HeatCell> heatmap) {
}
