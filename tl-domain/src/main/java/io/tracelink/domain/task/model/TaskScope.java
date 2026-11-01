package io.tracelink.domain.task.model;

import java.util.List;

/**
 * 数据范围。scopeDeptIds 为空表示不按部门收窄；selfOnly 为真时只取与本人有关的工单（SELF 范围）。
 * 过滤参数由应用服务显式传给仓储，不在仓储里拼 SQL 判断角色（DESIGN 5.4）。
 */
public record TaskScope(List<Long> deptIds, Long employeeId, boolean selfOnly) {

    public static TaskScope all(Long employeeId) {
        return new TaskScope(null, employeeId, false);
    }
}
