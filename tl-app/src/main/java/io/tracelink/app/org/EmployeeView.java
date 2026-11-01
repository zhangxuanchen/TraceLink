package io.tracelink.app.org;

import java.time.LocalDate;

public record EmployeeView(
        Long id,
        String empNo,
        String name,
        Long deptId,
        String deptName,
        Long positionId,
        String positionName,
        Long managerId,
        String managerName,
        String phone,
        String email,
        Integer status,
        LocalDate joinDate) {
}
