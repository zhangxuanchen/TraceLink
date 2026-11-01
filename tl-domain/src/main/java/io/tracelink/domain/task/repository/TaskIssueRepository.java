package io.tracelink.domain.task.repository;

import io.tracelink.domain.task.model.TaskIssue;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskIssueRepository {

    void insert(TaskIssue issue);

    List<TaskIssue> listByTask(Long taskId);

    TaskIssue findById(Long id);

    void close(Long id, LocalDateTime closedAt);

    int countOpenByTask(Long taskId);
}
