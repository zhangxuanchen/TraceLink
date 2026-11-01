package io.tracelink.infra.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.chat.model.ChatScopeType;
import io.tracelink.domain.chat.model.ChatSessionStatus;

import java.time.LocalDateTime;

@TableName("chat_session")
public class ChatSessionEntity {

    @TableId(type = IdType.AUTO)
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
}
