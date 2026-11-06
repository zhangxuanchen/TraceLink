package io.tracelink.domain.admin.model;

import java.util.List;

/** 角色与权限点。数据范围宽度由角色决定，人员可兼多角色，取最宽的一个 */
public record RoleInfo(
        Long id,
        String code,
        String name,
        String dataScopeType,
        String remark,
        List<String> permissions) {
}
