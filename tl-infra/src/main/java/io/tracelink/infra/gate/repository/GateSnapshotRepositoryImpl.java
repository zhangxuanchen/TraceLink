package io.tracelink.infra.gate.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.gate.model.TaskGateSnapshot;
import io.tracelink.domain.gate.repository.GateSnapshotRepository;
import io.tracelink.infra.gate.entity.TaskGateSnapshotEntity;
import io.tracelink.infra.gate.mapper.TaskGateSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class GateSnapshotRepositoryImpl implements GateSnapshotRepository {

    private static final int BATCH = 500;

    private final TaskGateSnapshotMapper mapper;

    public GateSnapshotRepositoryImpl(TaskGateSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void deleteByDate(LocalDate date) {
        mapper.delete(new QueryWrapper<TaskGateSnapshotEntity>().eq("snap_date", date));
    }

    @Override
    public void insertAll(List<TaskGateSnapshot> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i += BATCH) {
            List<TaskGateSnapshotEntity> part = rows.subList(i, Math.min(i + BATCH, rows.size())).stream()
                    .map(GateSnapshotRepositoryImpl::toEntity)
                    .toList();
            mapper.insertBatch(part);
        }
    }

    @Override
    public LocalDate latestDate() {
        List<TaskGateSnapshotEntity> rows = mapper.selectList(new QueryWrapper<TaskGateSnapshotEntity>()
                .select("snap_date")
                .orderByDesc("snap_date")
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0).snapDate;
    }

    @Override
    public LocalDate latestDateBefore(LocalDate date) {
        List<TaskGateSnapshotEntity> rows = mapper.selectList(new QueryWrapper<TaskGateSnapshotEntity>()
                .select("snap_date")
                .lt("snap_date", date)
                .orderByDesc("snap_date")
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0).snapDate;
    }

    @Override
    public List<TaskGateSnapshot> findByDate(LocalDate date) {
        return mapper.selectList(new QueryWrapper<TaskGateSnapshotEntity>()
                        .eq("snap_date", date)
                        .orderByAsc("task_id")
                        .orderByAsc("gate_code"))
                .stream().map(GateSnapshotRepositoryImpl::toModel).toList();
    }

    @Override
    public List<TaskGateSnapshot> findByDateAndDepts(LocalDate date, List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        List<TaskGateSnapshot> rows = new ArrayList<>();
        for (int i = 0; i < deptIds.size(); i += BATCH) {
            List<Long> part = deptIds.subList(i, Math.min(i + BATCH, deptIds.size()));
            rows.addAll(mapper.selectList(new QueryWrapper<TaskGateSnapshotEntity>()
                            .eq("snap_date", date)
                            .in("dept_id", part)
                            .orderByAsc("task_id")
                            .orderByAsc("gate_code"))
                    .stream().map(GateSnapshotRepositoryImpl::toModel).toList());
        }
        return rows;
    }

    @Override
    public List<TaskGateSnapshot> findLatestByTaskBefore(Long taskId, LocalDate date) {
        return mapper.selectList(new QueryWrapper<TaskGateSnapshotEntity>()
                        .eq("task_id", taskId)
                        .lt("snap_date", date)
                        .apply("snap_date = (SELECT MAX(s.snap_date) FROM task_gate_snapshot s"
                                + " WHERE s.task_id = {0} AND s.snap_date < {1})", taskId, date)
                        .orderByAsc("gate_code"))
                .stream().map(GateSnapshotRepositoryImpl::toModel).toList();
    }

    @Override
    public List<LocalDate> findDatesBefore(LocalDate date) {
        return mapper.selectList(new QueryWrapper<TaskGateSnapshotEntity>()
                        .select("snap_date")
                        .lt("snap_date", date)
                        .groupBy("snap_date")
                        .orderByAsc("snap_date"))
                .stream().map(e -> e.snapDate).toList();
    }

    @Override
    public void deleteBefore(LocalDate date) {
        mapper.delete(new QueryWrapper<TaskGateSnapshotEntity>().lt("snap_date", date));
    }

    @Override
    public long countByDate(LocalDate date) {
        return mapper.selectCount(new QueryWrapper<TaskGateSnapshotEntity>().eq("snap_date", date));
    }

    public static TaskGateSnapshot toModel(TaskGateSnapshotEntity e) {
        TaskGateSnapshot s = new TaskGateSnapshot();
        s.id = e.id;
        s.snapDate = e.snapDate;
        s.taskId = e.taskId;
        s.deptId = e.deptId;
        s.taskType = e.taskType;
        s.gateCode = e.gateCode;
        s.state = e.state;
        s.gapDays = e.gapDays;
        s.holderId = e.holderId;
        return s;
    }

    private static TaskGateSnapshotEntity toEntity(TaskGateSnapshot s) {
        TaskGateSnapshotEntity e = new TaskGateSnapshotEntity();
        e.snapDate = s.snapDate;
        e.taskId = s.taskId;
        e.deptId = s.deptId;
        e.taskType = s.taskType;
        e.gateCode = s.gateCode;
        e.state = s.state;
        e.gapDays = s.gapDays;
        e.holderId = s.holderId;
        return e;
    }
}
