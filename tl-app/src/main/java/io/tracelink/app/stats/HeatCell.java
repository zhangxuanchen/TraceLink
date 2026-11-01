package io.tracelink.app.stats;

/** 部门周热力的一格：部门 × 周 × 逾期单数 */
public record HeatCell(String row, String column, int value) {
}
