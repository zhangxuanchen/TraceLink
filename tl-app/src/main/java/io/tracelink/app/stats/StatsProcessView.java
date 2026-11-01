package io.tracelink.app.stats;

import java.util.List;

/** 工序维度：明细行 + 按工序汇总 + 按日合计 */
public record StatsProcessView(
        String from,
        String to,
        List<ProcessRow> rows,
        List<PairPoint> byProcess,
        List<PairPoint> byDate,
        double rate) {
}
