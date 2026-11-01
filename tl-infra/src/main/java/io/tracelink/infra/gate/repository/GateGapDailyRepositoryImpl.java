package io.tracelink.infra.gate.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.gate.model.GateGapDaily;
import io.tracelink.domain.gate.repository.GateGapDailyRepository;
import io.tracelink.infra.gate.entity.GateGapDailyEntity;
import io.tracelink.infra.gate.mapper.GateGapDailyMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class GateGapDailyRepositoryImpl implements GateGapDailyRepository {

    private final GateGapDailyMapper mapper;

    public GateGapDailyRepositoryImpl(GateGapDailyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void deleteByDate(LocalDate date) {
        mapper.delete(new QueryWrapper<GateGapDailyEntity>().eq("snap_date", date));
    }

    @Override
    public void insertAll(List<GateGapDaily> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        mapper.insertBatch(rows.stream().map(GateGapDailyRepositoryImpl::toEntity).toList());
    }

    @Override
    public List<GateGapDaily> findByDate(LocalDate date) {
        return mapper.selectList(new QueryWrapper<GateGapDailyEntity>()
                        .eq("snap_date", date)
                        .orderByAsc("dept_id")
                        .orderByAsc("gate_code"))
                .stream().map(GateGapDailyRepositoryImpl::toModel).toList();
    }

    @Override
    public List<GateGapDaily> findByDateAndDepts(LocalDate date, List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new QueryWrapper<GateGapDailyEntity>()
                        .eq("snap_date", date)
                        .in("dept_id", deptIds)
                        .orderByAsc("dept_id")
                        .orderByAsc("gate_code"))
                .stream().map(GateGapDailyRepositoryImpl::toModel).toList();
    }

    public static GateGapDaily toModel(GateGapDailyEntity e) {
        GateGapDaily g = new GateGapDaily();
        g.id = e.id;
        g.snapDate = e.snapDate;
        g.deptId = e.deptId;
        g.gateCode = e.gateCode;
        g.doneCount = e.doneCount;
        g.todoCount = e.todoCount;
        g.naCount = e.naCount;
        g.maxGapDays = e.maxGapDays;
        return g;
    }

    private static GateGapDailyEntity toEntity(GateGapDaily g) {
        GateGapDailyEntity e = new GateGapDailyEntity();
        e.snapDate = g.snapDate;
        e.deptId = g.deptId;
        e.gateCode = g.gateCode;
        e.doneCount = g.doneCount;
        e.todoCount = g.todoCount;
        e.naCount = g.naCount;
        e.maxGapDays = g.maxGapDays;
        return e;
    }
}
