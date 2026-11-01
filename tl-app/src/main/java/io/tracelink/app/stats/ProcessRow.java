package io.tracelink.app.stats;

/** 工序明细行 */
public record ProcessRow(String date, String workshop, String process, double planned, double actual, double rate) {
}
