package io.tracelink.infra.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.task.model.CheckinResultType;
import io.tracelink.domain.task.model.TaskCheckin;
import io.tracelink.domain.task.repository.TaskCheckinRepository;
import io.tracelink.infra.task.entity.TaskCheckinEntity;
import io.tracelink.infra.task.mapper.TaskCheckinMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TaskCheckinRepositoryImpl implements TaskCheckinRepository {

    private final TaskCheckinMapper checkinMapper;

    public TaskCheckinRepositoryImpl(TaskCheckinMapper checkinMapper) {
        this.checkinMapper = checkinMapper;
    }

    @Override
    public void insert(TaskCheckin checkin) {
        TaskCheckinEntity entity = toEntity(checkin);
        entity.id = null;
        checkinMapper.insert(entity);
        checkin.id = entity.id;
        checkin.createdAt = entity.createdAt;
    }

    @Override
    public List<TaskCheckin> listByTask(Long taskId) {
        return checkinMapper.selectList(new QueryWrapper<TaskCheckinEntity>()
                        .eq("task_id", taskId)
                        .orderByDesc("check_date")
                        .orderByDesc("created_at")
                        .orderByDesc("id"))
                .stream().map(TaskCheckinRepositoryImpl::toModel).toList();
    }

    @Override
    public List<TaskCheckin> listByTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        List<TaskCheckin> rows = new java.util.ArrayList<>();
        for (int i = 0; i < taskIds.size(); i += 500) {
            List<Long> part = taskIds.subList(i, Math.min(i + 500, taskIds.size()));
            rows.addAll(checkinMapper.selectList(new QueryWrapper<TaskCheckinEntity>()
                            .in("task_id", part)
                            .orderByDesc("check_date")
                            .orderByDesc("created_at")
                            .orderByDesc("id"))
                    .stream().map(TaskCheckinRepositoryImpl::toModel).toList());
        }
        return rows;
    }

    @Override
    public int maxProgress(Long taskId) {
        List<TaskCheckinEntity> rows = checkinMapper.selectList(new QueryWrapper<TaskCheckinEntity>()
                .eq("task_id", taskId)
                .orderByDesc("progress")
                .last("LIMIT 1"));
        return rows.isEmpty() ? 0 : rows.get(0).progress;
    }

    @Override
    public int countByResultType(Long taskId, CheckinResultType resultType) {
        return Math.toIntExact(checkinMapper.selectCount(new QueryWrapper<TaskCheckinEntity>()
                .eq("task_id", taskId)
                .eq("result_type", resultType.name())));
    }

    @Override
    public boolean existsByTask(Long taskId) {
        return checkinMapper.selectCount(new QueryWrapper<TaskCheckinEntity>().eq("task_id", taskId)) > 0;
    }

    public static TaskCheckin toModel(TaskCheckinEntity e) {
        TaskCheckin c = new TaskCheckin();
        c.id = e.id;
        c.taskId = e.taskId;
        c.employeeId = e.employeeId;
        c.checkDate = e.checkDate;
        c.progress = e.progress;
        c.content = e.content;
        c.blockerText = e.blockerText;
        c.nextStep = e.nextStep;
        c.planEndSnapshot = e.planEndSnapshot;
        c.resultType = e.resultType;
        c.closureLevel = e.closureLevel;
        c.createdAt = e.createdAt;
        return c;
    }

    private static TaskCheckinEntity toEntity(TaskCheckin c) {
        TaskCheckinEntity e = new TaskCheckinEntity();
        e.id = c.id;
        e.taskId = c.taskId;
        e.employeeId = c.employeeId;
        e.checkDate = c.checkDate;
        e.progress = c.progress;
        e.content = c.content;
        e.blockerText = c.blockerText;
        e.nextStep = c.nextStep;
        e.planEndSnapshot = c.planEndSnapshot;
        e.resultType = c.resultType;
        e.closureLevel = c.closureLevel;
        return e;
    }
}
