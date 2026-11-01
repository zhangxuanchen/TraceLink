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

    /** 统计用：按创建时间取范围（含部门过滤，null 表示全部） */
    List<Task> findCreatedBetween(LocalDateTime from, LocalDateTime to, Long deptId);

    /** 统计用：按实际完成时间取范围 */
    List<Task> findDoneBetween(LocalDateTime from, LocalDateTime to, Long deptId);

    /** 统计与台账用：计划完成时间落在区间内的工单（补上「补录的历史工单创建时间不在区间」的口径缺口） */
    List<Task> findPlannedBetween(LocalDateTime from, LocalDateTime to, Long deptId);

    List<Task> findByIds(List<Long> ids);

    /** 导入去重：取某部门自某时刻起的全部未删除工单用于标题比对 */
    List<Task> findForDedup(Long deptId, LocalDateTime since);

    /** 完成地图快照用：全部在办工单（待接单、进行中、阻碍中、待验收） */
    List<Task> findOpenAll(int limit);

    /** 事项完成地图用：某工单的直接子工单 */
    List<Task> findChildren(Long parentId);

    /** 事项完成地图用：由某工单复发而来的后续工单 */
    List<Task> findRecurrencesOf(Long taskId);
}
