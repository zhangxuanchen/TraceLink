package io.tracelink.domain.task.repository;

import io.tracelink.domain.task.model.TaskAssignee;

import java.util.List;

public interface TaskAssigneeRepository {

    void insert(TaskAssignee assignee);

    List<TaskAssignee> listByTask(Long taskId);

    boolean exists(Long taskId, Long employeeId);
}
