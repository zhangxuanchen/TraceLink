package io.tracelink.infra.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.chat.model.ChatRole;

import java.time.LocalDateTime;

@TableName("chat_message")
public class ChatMessageEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long sessionId;
    public ChatRole role;
    public String content;
    public String citations;
    public String toolCalls;
    public String modelName;
    public String promptVer;
    public Integer tokenIn;
    public Integer tokenOut;
    public Integer latencyMs;
    public LocalDateTime createdAt;
}
