package io.tracelink.domain.chat.model;

import java.time.LocalDateTime;

/**
 * 会话消息。citations 与 toolCalls 以 JSON 字符串保存，
 * 页面重开时按原文还原依据，不需要重跑模型（DESIGN 4.8）。
 */
public class ChatMessage {

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
