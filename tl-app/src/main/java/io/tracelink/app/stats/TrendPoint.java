package io.tracelink.app.stats;

/** 趋势点：一天的新增、完成与在办存量 */
public record TrendPoint(String date, int created, int done, int open) {
}
