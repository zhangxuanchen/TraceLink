package io.tracelink.api.dto;

import java.util.List;

/** 账号角色整组替换：传什么就是什么，空数组表示清空角色 */
public record AccountRolesRequest(List<String> roles) {
}
