package io.tracelink.domain.stat.repository;

import io.tracelink.domain.stat.model.StatDaily;
import io.tracelink.domain.stat.model.StatDimType;

import java.time.LocalDate;
import java.util.List;

public interface StatDailyRepository {

    void deleteByDate(LocalDate date);

    void upsertAll(List<StatDaily> rows);

    List<StatDaily> findBetween(LocalDate from, LocalDate to);

    List<StatDaily> find(LocalDate date, StatDimType dimType);
}
