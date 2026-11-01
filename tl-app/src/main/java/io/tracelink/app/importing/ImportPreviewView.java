package io.tracelink.app.importing;

import java.util.List;

/** 预览页：批次 + 每页摘要 + 行列表 + 识别率 + 生产块指标 */
public record ImportPreviewView(ImportBatchView batch, List<SheetSummary> sheets, List<ImportRowView> rows,
                                RecognitionView recognition, List<MetricView> metrics, String signature) {

    public record SheetSummary(String name, String title, String reporterName, String reportDate,
                               int rowCount, int metricCount, boolean empty) {
    }

    /**
     * 识别率口径：明细行识别率 = 有标题且（有责任人或汇报结果）的行占全部明细行；
     * 人名匹配率 = 命中的姓名出现次数占全部人物出现次数（组织短语不计入分母）。
     */
    public record RecognitionView(int detailRows, int rowsWithAssignee, int personMentions, int personMatched,
                                 double rowRate, double personRate, double rowTarget, double personTarget,
                                 boolean rowPass, boolean personPass) {
    }

    public record MetricView(String statDate, String workshop, String process, String planQty, String actQty) {
    }
}
