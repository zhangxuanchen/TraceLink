package io.tracelink.domain.task.repository;

import io.tracelink.domain.task.model.CheckinResultType;
import io.tracelink.domain.task.model.TaskCheckin;

import java.util.List;

public interface TaskCheckinRepository {

    void insert(TaskCheckin checkin);

    List<TaskCheckin> listByTask(Long taskId);

    int maxProgress(Long taskId);

    int countByResultType(Long taskId, CheckinResultType resultType);

    boolean existsByTask(Long taskId);
}
