package io.tracelink.app.importing;

public record DailyReportView(Long id, String reportDate, Long reporterId, String reporterName,
                              String sourceSheet, int rowCount, int metricCount, String createdAt) {
}
