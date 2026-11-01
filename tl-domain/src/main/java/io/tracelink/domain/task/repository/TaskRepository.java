package io.tracelink.domain.task.repository;

import io.tracelink.common.api.PageResult;
import io.tracelink.domain.task.model.Task;
import io.tracelink.domain.task.model.TaskQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository {

    /** 生成 T-20261101-001 形式的编号，按日重新计数 */
    String nextTaskNo(LocalDate day);

    void insert(Task task);

    void update(Task task);

    Task findById(Long id);

    PageResult<Task> page(TaskQuery query);

    long count(TaskQuery query);

    List<Task> findOpenByDept(Long deptId);

    List<Task> findDoneSince(Long deptId, LocalDateTime since);
}
