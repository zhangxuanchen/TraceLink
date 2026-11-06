package io.tracelink.domain.task.model;

import java.time.LocalDateTime;
import java.util.List;

/** 列表查询条件。数据范围通过 scope 传入，mine 表示「与我有关」；状态、类型、部门可多选 */
public record TaskQuery(
        String keyword,
        List<TaskStatus> statuses,
        List<TaskType> types,
        List<Long> deptIds,
        Long ownerId,
        Long primaryAssigneeId,
        boolean mine,
        boolean overdueOnly,
        boolean blockedOnly,
        LocalDateTime now,
        TaskScope scope,
        int pageNo,
        int pageSize) {
}
