package io.tracelink.domain.task.repository;

import io.tracelink.domain.task.model.TaskAction;

import java.util.List;

public interface TaskActionRepository {

    void insert(TaskAction action);

    List<TaskAction> listByTask(Long taskId);

    /** 快照批量判定用：一次取多张工单的措施项 */
    List<TaskAction> listByTaskIds(List<Long> taskIds);

    TaskAction findById(Long id);

    void markDone(Long id);

    int countOpenByTask(Long taskId);
}
