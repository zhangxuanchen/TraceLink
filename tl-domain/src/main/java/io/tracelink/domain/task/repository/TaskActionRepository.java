package io.tracelink.domain.task.repository;

import io.tracelink.domain.task.model.TaskAction;

import java.util.List;

public interface TaskActionRepository {

    void insert(TaskAction action);

    List<TaskAction> listByTask(Long taskId);

    TaskAction findById(Long id);

    void markDone(Long id);

    int countOpenByTask(Long taskId);
}
