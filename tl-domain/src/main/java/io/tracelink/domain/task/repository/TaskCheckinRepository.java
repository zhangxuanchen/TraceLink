package io.tracelink.domain.task.repository;

import io.tracelink.domain.task.model.CheckinResultType;
import io.tracelink.domain.task.model.TaskCheckin;

import java.util.List;

public interface TaskCheckinRepository {

    void insert(TaskCheckin checkin);

    List<TaskCheckin> listByTask(Long taskId);

    /** 快照批量判定用：一次取多张工单的追踪记录 */
    List<TaskCheckin> listByTaskIds(List<Long> taskIds);

    int maxProgress(Long taskId);

    int countByResultType(Long taskId, CheckinResultType resultType);

    boolean existsByTask(Long taskId);
}
