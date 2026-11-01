package io.tracelink.domain.task.model;

import java.time.LocalDateTime;

/** 列表查询条件。数据范围通过 scope 传入，mine 表示「与我有关」 */
public record TaskQuery(
        String keyword,
        TaskStatus status,
        TaskType type,
        Long deptId,
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
