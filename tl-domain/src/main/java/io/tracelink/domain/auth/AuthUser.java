package io.tracelink.domain.auth;

import java.util.List;

public record AuthUser(
        Long employeeId,
        String username,
        String name,
        Long deptId,
        List<String> roles,
        String dataScopeType) {
}
