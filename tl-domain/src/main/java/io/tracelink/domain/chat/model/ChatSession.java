package io.tracelink.domain.chat.model;

import java.time.LocalDateTime;

/** 会话。归属人唯一，管理者只读；title 取首轮提问前四十字 */
public class ChatSession {

    public Long id;
    public Long employeeId;
    public String title;
    public ChatScopeType scopeType;
    public Long scopeId;
    public Integer msgCount;
    public LocalDateTime lastMsgAt;
    public ChatSessionStatus status;
    public Boolean deleted;
    public LocalDateTime createdAt;

    public String statusValue() {
        return status == null ? ChatSessionStatus.OPEN.name() : status.name();
    }

    public int msgCountValue() {
        return msgCount == null ? 0 : msgCount;
    }
}
