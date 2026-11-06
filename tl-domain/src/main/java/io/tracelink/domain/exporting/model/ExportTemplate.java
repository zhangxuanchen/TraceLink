package io.tracelink.domain.exporting.model;

/** 六种固定格式模板，见 DESIGN 4.6 */
public enum ExportTemplate {

    DAILY("日报回填"),
    WEEKLY("周报汇总"),
    LEDGER("工单台账"),
    PERF("绩效数据"),
    CHART("统计图表"),
    RATES("部门与个人率表");

    private final String label;

    ExportTemplate(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
