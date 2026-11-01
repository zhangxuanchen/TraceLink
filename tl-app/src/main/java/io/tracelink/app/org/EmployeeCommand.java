package io.tracelink.app.org;

import java.time.LocalDate;

public record EmployeeCommand(
        String empNo,
        String name,
        Long deptId,
        Long positionId,
        Long managerId,
        String phone,
        String email,
        Integer status,
        LocalDate joinDate) {
}
