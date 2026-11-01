package io.tracelink.app.org;

public record PositionCommand(
        Long deptId,
        String code,
        String name,
        Integer jobLevel,
        Integer isManager,
        Integer sortNo,
        Integer status) {
}
