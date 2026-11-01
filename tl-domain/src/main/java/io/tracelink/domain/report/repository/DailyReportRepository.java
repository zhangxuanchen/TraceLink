package io.tracelink.domain.report.repository;

import io.tracelink.domain.report.model.DailyReport;

import java.time.LocalDate;
import java.util.List;

public interface DailyReportRepository {

    DailyReport findByDateAndReporter(LocalDate reportDate, Long reporterId);

    List<DailyReport> listBetween(LocalDate from, LocalDate to, Long reporterId);

    List<DailyReport> listByBatch(Long batchId);

    void upsert(DailyReport report);
}
