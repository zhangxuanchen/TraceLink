package io.tracelink.app.stats;

/** 单个 KPI。公式写在 formula 里，页面与导出引用同一份定义（DESIGN 4.4） */
public record KpiView(String code, String label, double value, String unit, String formula) {
}
