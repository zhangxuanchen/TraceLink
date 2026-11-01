package io.tracelink.infra.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.task.model.ActionStatus;
import io.tracelink.domain.task.model.TaskAction;
import io.tracelink.domain.task.repository.TaskActionRepository;
import io.tracelink.infra.task.entity.TaskActionEntity;
import io.tracelink.infra.task.mapper.TaskActionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TaskActionRepositoryImpl implements TaskActionRepository {

    private final TaskActionMapper actionMapper;

    public TaskActionRepositoryImpl(TaskActionMapper actionMapper) {
        this.actionMapper = actionMapper;
    }

    @Override
    public void insert(TaskAction action) {
        TaskActionEntity entity = toEntity(action);
        entity.id = null;
        actionMapper.insert(entity);
        action.id = entity.id;
    }

    @Override
    public List<TaskAction> listByTask(Long taskId) {
        return actionMapper.selectList(new QueryWrapper<TaskActionEntity>()
                        .eq("task_id", taskId)
                        .orderByAsc("seq")
                        .orderByAsc("id"))
                .stream().map(TaskActionRepositoryImpl::toModel).toList();
    }

    @Override
    public List<TaskAction> listByTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        List<TaskAction> rows = new java.util.ArrayList<>();
        for (int i = 0; i < taskIds.size(); i += 500) {
            List<Long> part = taskIds.subList(i, Math.min(i + 500, taskIds.size()));
            rows.addAll(actionMapper.selectList(new QueryWrapper<TaskActionEntity>()
                            .in("task_id", part)
                            .orderByAsc("task_id")
                            .orderByAsc("seq"))
                    .stream().map(TaskActionRepositoryImpl::toModel).toList());
        }
        return rows;
    }

    @Override
    public TaskAction findById(Long id) {
        TaskActionEntity entity = actionMapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public void markDone(Long id) {
        actionMapper.update(null, new UpdateWrapper<TaskActionEntity>()
                .eq("id", id)
                .eq("status", ActionStatus.OPEN.name())
                .set("status", ActionStatus.DONE.name()));
    }

    @Override
    public int countOpenByTask(Long taskId) {
        return Math.toIntExact(actionMapper.selectCount(new QueryWrapper<TaskActionEntity>()
                .eq("task_id", taskId)
                .eq("status", ActionStatus.OPEN.name())));
    }

    public static TaskAction toModel(TaskActionEntity e) {
        TaskAction a = new TaskAction();
        a.id = e.id;
        a.taskId = e.taskId;
        a.issueId = e.issueId;
        a.seq = e.seq;
        a.content = e.content;
        a.ownerId = e.ownerId;
        a.dueDate = e.dueDate;
        a.status = e.status;
        a.taskChildId = e.taskChildId;
        return a;
    }

    private static TaskActionEntity toEntity(TaskAction a) {
        TaskActionEntity e = new TaskActionEntity();
        e.id = a.id;
        e.taskId = a.taskId;
        e.issueId = a.issueId;
        e.seq = a.seq;
        e.content = a.content;
        e.ownerId = a.ownerId;
        e.dueDate = a.dueDate;
        e.status = a.status;
        e.taskChildId = a.taskChildId;
        return e;
    }
}
