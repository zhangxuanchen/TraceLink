package io.tracelink.infra.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.task.model.TaskAssignee;
import io.tracelink.domain.task.repository.TaskAssigneeRepository;
import io.tracelink.infra.task.entity.TaskAssigneeEntity;
import io.tracelink.infra.task.mapper.TaskAssigneeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TaskAssigneeRepositoryImpl implements TaskAssigneeRepository {

    private final TaskAssigneeMapper assigneeMapper;

    public TaskAssigneeRepositoryImpl(TaskAssigneeMapper assigneeMapper) {
        this.assigneeMapper = assigneeMapper;
    }

    @Override
    public void insert(TaskAssignee assignee) {
        TaskAssigneeEntity entity = new TaskAssigneeEntity();
        entity.taskId = assignee.taskId;
        entity.employeeId = assignee.employeeId;
        entity.roleType = assignee.roleType;
        assigneeMapper.insert(entity);
        assignee.id = entity.id;
    }

    @Override
    public List<TaskAssignee> listByTask(Long taskId) {
        return assigneeMapper.selectList(new QueryWrapper<TaskAssigneeEntity>()
                        .eq("task_id", taskId)
                        .orderByAsc("id"))
                .stream().map(e -> {
                    TaskAssignee a = new TaskAssignee();
                    a.id = e.id;
                    a.taskId = e.taskId;
                    a.employeeId = e.employeeId;
                    a.roleType = e.roleType;
                    return a;
                }).toList();
    }

    @Override
    public List<TaskAssignee> listByTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        List<TaskAssignee> rows = new java.util.ArrayList<>();
        for (int i = 0; i < taskIds.size(); i += 500) {
            List<Long> part = taskIds.subList(i, Math.min(i + 500, taskIds.size()));
            rows.addAll(assigneeMapper.selectList(new QueryWrapper<TaskAssigneeEntity>()
                            .in("task_id", part)
                            .orderByAsc("id"))
                    .stream().map(e -> {
                        TaskAssignee a = new TaskAssignee();
                        a.id = e.id;
                        a.taskId = e.taskId;
                        a.employeeId = e.employeeId;
                        a.roleType = e.roleType;
                        return a;
                    }).toList());
        }
        return rows;
    }

    @Override
    public boolean exists(Long taskId, Long employeeId) {
        return assigneeMapper.selectCount(new QueryWrapper<TaskAssigneeEntity>()
                .eq("task_id", taskId)
                .eq("employee_id", employeeId)) > 0;
    }
}
