package io.tracelink.domain.task.repository;

import io.tracelink.domain.task.model.TaskAssignee;

import java.util.List;

public interface TaskAssigneeRepository {

    void insert(TaskAssignee assignee);

    List<TaskAssignee> listByTask(Long taskId);

    /** 快照与矩阵按数据范围过滤用：一次取多张工单的参与人 */
    List<TaskAssignee> listByTaskIds(List<Long> taskIds);

    boolean exists(Long taskId, Long employeeId);

    /** 评价「协办力」用：本人以协办身份参与过的工单数 */
    int countByEmployee(Long employeeId);
}
