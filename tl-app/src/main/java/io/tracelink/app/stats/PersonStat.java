package io.tracelink.app.stats;

/** 人员产出散点的一行：量、质、逾期同时看 */
public record PersonStat(
        Long employeeId,
        String name,
        String deptName,
        int created,
        int due,
        int done,
        int open,
        int overdue,
        int rejected,
        double onTimeRate,
        double evidenceRate,
        double avgHours) {
}
