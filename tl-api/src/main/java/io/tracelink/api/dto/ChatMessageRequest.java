package io.tracelink.api.dto;

import java.util.List;

/** 会话提问入参（CHT-3）：scope 不传取本部门，allowTools 默认 true */
public record ChatMessageRequest(String question, ChatScopeRequest scope, Boolean allowTools) {

    public record ChatScopeRequest(Long deptId, List<String> taskTypes) {
    }
}
