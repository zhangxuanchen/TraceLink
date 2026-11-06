package io.tracelink.domain.admin.model;

import java.util.List;

/** 账号与角色。没有账号的人员也列出来，页面提示先建账号 */
public record AccountInfo(
        Long employeeId,
        String empNo,
        String name,
        String deptName,
        String username,
        int status,
        List<String> roleCodes,
        String dataScopeType) {
}
