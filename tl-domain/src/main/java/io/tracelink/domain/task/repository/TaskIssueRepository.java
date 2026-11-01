package io.tracelink.domain.task.repository;

import io.tracelink.domain.task.model.TaskIssue;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskIssueRepository {

    void insert(TaskIssue issue);

    List<TaskIssue> listByTask(Long taskId);

    /** 快照批量判定用：一次取多张工单的问题项 */
    List<TaskIssue> listByTaskIds(List<Long> taskIds);

    TaskIssue findById(Long id);

    void close(Long id, LocalDateTime closedAt);

    int countOpenByTask(Long taskId);
}
