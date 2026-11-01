package io.tracelink.domain.stat.repository;

import io.tracelink.domain.stat.model.TaskMetric;

import java.time.LocalDate;
import java.util.List;

public interface TaskMetricRepository {

    void deleteByDateRange(LocalDate from, LocalDate to);

    void upsertAll(List<TaskMetric> metrics);

    List<TaskMetric> findProcess(LocalDate from, LocalDate to);

    List<TaskMetric> findBetween(LocalDate from, LocalDate to);

    /** G8 用：某张工单名下的指标；没有指标时 G8 判为不适用 */
    List<TaskMetric> findByTaskIds(List<Long> taskIds);
}
