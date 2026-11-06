package io.tracelink.app.stats;

import java.util.List;

/**
 * 工单中心顶部口径指标的可选筛选条件，字段与工单列表的筛选条一一对应。
 * null/false 表示不过滤；只要有任意一项生效，指标就只在筛选出的工单上算。
 * 状态、类型、部门支持多选，用列表传；部门落在指标谓词里过滤，不改快照范围。
 */
public record KpiFilter(String keyword, List<String> statuses, List<String> types, List<Long> deptIds,
                        boolean overdue, boolean blocked, boolean mine) {

    public static final KpiFilter EMPTY = new KpiFilter(null, null, null, null, false, false, false);

    public boolean active() {
        return (keyword != null && !keyword.isBlank())
                || (statuses != null && !statuses.isEmpty())
                || (types != null && !types.isEmpty())
                || (deptIds != null && !deptIds.isEmpty())
                || overdue || blocked || mine;
    }
}
