package io.tracelink.domain.gate.repository;

import io.tracelink.domain.gate.model.GateGapDaily;

import java.time.LocalDate;
import java.util.List;

public interface GateGapDailyRepository {

    void deleteByDate(LocalDate date);

    void insertAll(List<GateGapDaily> rows);

    List<GateGapDaily> findByDate(LocalDate date);

    List<GateGapDaily> findByDateAndDepts(LocalDate date, List<Long> deptIds);
}
