package io.tracelink.app.gate;

import java.util.List;

/** CMP-3：缺口聚合与滞留排行，口径与矩阵列合计一致。 */
public record GapRankView(
        String snapshotAt,
        boolean stale,
        int totalTodo,
        List<ByGate> byGate,
        List<ByHolder> byHolder,
        List<ByDept> byDept,
        List<TopTask> top) {

    public record ByGate(String code, String name, int todoCount, int maxGapDays) {
    }

    public record ByHolder(Long holderId, String holderName, int todoCount, int maxGapDays) {
    }

    public record ByDept(Long deptId, String deptName, int todoCount, int totalCount, int maxGapDays) {
    }

    public record TopTask(Long taskId, String taskNo, String title, String gateCode, String gateName,
                          int gapDays, String holderName) {
    }
}
