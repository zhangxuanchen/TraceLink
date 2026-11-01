package io.tracelink.app.stats;

/** 部门对比：驾驶舱的完成率横条与部门看板共用 */
public record DeptStat(
        Long deptId,
        String name,
        int created,
        int done,
        int overdue,
        int open,
        double completionRate,
        double onTimeRate) {
}
