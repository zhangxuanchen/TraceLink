package io.tracelink.domain.gate.repository;

import io.tracelink.domain.gate.model.TaskGateSnapshot;

import java.time.LocalDate;
import java.util.List;

public interface GateSnapshotRepository {

    void deleteByDate(LocalDate date);

    void insertAll(List<TaskGateSnapshot> rows);

    /** 最近一次快照日期，没有快照返回 null */
    LocalDate latestDate();

    /** 目标日期之前最近一次快照日期 */
    LocalDate latestDateBefore(LocalDate date);

    List<TaskGateSnapshot> findByDate(LocalDate date);

    List<TaskGateSnapshot> findByDateAndDepts(LocalDate date, List<Long> deptIds);

    /** 某工单在指定日期之前最近一日的条件行，用于实时算式继承滞留天数 */
    List<TaskGateSnapshot> findLatestByTaskBefore(Long taskId, LocalDate date);

    List<LocalDate> findDatesBefore(LocalDate date);

    void deleteBefore(LocalDate date);

    long countByDate(LocalDate date);
}
