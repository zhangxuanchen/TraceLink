package io.tracelink.app.settings;

import java.util.List;

/** 角色与权限点 */
public record RoleView(
        Long id,
        String code,
        String name,
        String dataScopeType,
        String dataScopeLabel,
        String remark,
        List<String> permissions) {
}
