package io.tracelink.api.dto;

/** 新建会话入参（CHT-1）：标题可空，范围默认本部门 */
public record ChatSessionRequest(String title, String scopeType, Long scopeId) {
}
