package io.tracelink.app.org;

public record DeptCommand(
        String code,
        String name,
        Long parentId,
        Long leaderEmployeeId,
        Integer headcount,
        Integer sortNo,
        Integer status) {
}
