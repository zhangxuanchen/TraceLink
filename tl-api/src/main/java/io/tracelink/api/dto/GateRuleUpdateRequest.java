package io.tracelink.api.dto;

/** 完成条件规则修改：权重与是否启用，其它字段不支持在线改 */
public record GateRuleUpdateRequest(Integer weight, Boolean enabled) {
}
