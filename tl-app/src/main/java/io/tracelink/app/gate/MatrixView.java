package io.tracelink.app.gate;

import java.util.List;

/** CMP-2：部门缺口矩阵。行是在办工单，列是条件，格子三色。 */
public record MatrixView(
        String snapshotAt,
        boolean stale,
        List<Column> columns,
        List<Row> rows,
        long total) {

    public record Column(String code, String name, int todoCount) {
    }

    public record Row(
            Long taskId,
            String taskNo,
            String title,
            String assigneeName,
            boolean overdue,
            int overdueDays,
            int completionRate,
            List<Cell> cells) {
    }

    public record Cell(String code, String state, String stateLabel, int gapDays) {
    }
}
