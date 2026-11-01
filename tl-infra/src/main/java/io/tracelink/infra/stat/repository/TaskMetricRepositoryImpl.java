package io.tracelink.infra.stat.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.stat.model.StatDimType;
import io.tracelink.domain.stat.model.TaskMetric;
import io.tracelink.domain.stat.repository.TaskMetricRepository;
import io.tracelink.infra.stat.entity.TaskMetricEntity;
import io.tracelink.infra.stat.mapper.TaskMetricMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class TaskMetricRepositoryImpl implements TaskMetricRepository {

    private final TaskMetricMapper mapper;

    public TaskMetricRepositoryImpl(TaskMetricMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void deleteByDateRange(LocalDate from, LocalDate to) {
        mapper.delete(new QueryWrapper<TaskMetricEntity>()
                .ge("stat_date", from)
                .le("stat_date", to));
    }

    @Override
    public void upsertAll(List<TaskMetric> metrics) {
        for (TaskMetric metric : metrics) {
            TaskMetricEntity existing = mapper.selectOne(new QueryWrapper<TaskMetricEntity>()
                    .eq("stat_date", metric.statDate)
                    .eq("dim_type", metric.dimType == null ? null : metric.dimType.name())
                    .eq("dim_code", metric.dimCode)
                    .eq("metric_code", metric.metricCode)
                    .last("LIMIT 1"));
            if (existing == null) {
                TaskMetricEntity entity = new TaskMetricEntity();
                entity.taskId = metric.taskId;
                entity.statDate = metric.statDate;
                entity.dimType = metric.dimType;
                entity.dimCode = metric.dimCode;
                entity.metricCode = metric.metricCode;
                entity.value = metric.value;
                entity.unit = metric.unit;
                entity.extra = metric.extra;
                mapper.insert(entity);
                metric.id = entity.id;
            } else {
                existing.value = metric.value;
                existing.unit = metric.unit;
                existing.extra = metric.extra;
                existing.taskId = metric.taskId;
                mapper.updateById(existing);
                metric.id = existing.id;
            }
        }
    }

    @Override
    public List<TaskMetric> findProcess(LocalDate from, LocalDate to) {
        return mapper.selectList(new QueryWrapper<TaskMetricEntity>()
                        .eq("dim_type", StatDimType.PROCESS.name())
                        .ge("stat_date", from)
                        .le("stat_date", to)
                        .orderByAsc("stat_date"))
                .stream().map(TaskMetricRepositoryImpl::toModel).toList();
    }

    @Override
    public List<TaskMetric> findBetween(LocalDate from, LocalDate to) {
        return mapper.selectList(new QueryWrapper<TaskMetricEntity>()
                        .ge("stat_date", from)
                        .le("stat_date", to)
                        .orderByAsc("stat_date"))
                .stream().map(TaskMetricRepositoryImpl::toModel).toList();
    }

    @Override
    public List<TaskMetric> findByTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new QueryWrapper<TaskMetricEntity>()
                        .in("task_id", taskIds)
                        .orderByAsc("stat_date"))
                .stream().map(TaskMetricRepositoryImpl::toModel).toList();
    }

    public static TaskMetric toModel(TaskMetricEntity e) {
        TaskMetric m = new TaskMetric();
        m.id = e.id;
        m.taskId = e.taskId;
        m.statDate = e.statDate;
        m.dimType = e.dimType;
        m.dimCode = e.dimCode;
        m.metricCode = e.metricCode;
        m.value = e.value;
        m.unit = e.unit;
        m.extra = e.extra;
        return m;
    }
}
