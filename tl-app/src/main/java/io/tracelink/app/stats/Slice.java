package io.tracelink.app.stats;

/** 命名计数：状态环、逾期直方、漏斗、问题类型榜都用这一种形状 */
public record Slice(String label, int count) {
}
