package io.tracelink.app.settings;

import java.util.List;

/** 账号与角色；username 为空表示还没建账号 */
public record AccountView(
        Long employeeId,
        String empNo,
        String name,
        String deptName,
        String username,
        int status,
        String statusLabel,
        List<String> roleCodes,
        String dataScopeType,
        String dataScopeLabel) {
}
