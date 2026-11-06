package io.tracelink.domain.chat.repository;

import io.tracelink.domain.chat.model.ChatMessage;

import java.util.List;

public interface ChatMessageRepository {

    void insert(ChatMessage message);

    /** 会话历史按 id 升序，limit 取最近 N 条后仍按时间正序返回 */
    List<ChatMessage> listBySession(Long sessionId, int limit);

    ChatMessage findBySessionAndContent(Long sessionId, String role, String content);

    /** 清空一会话全部消息；返回删除条数，供审计与页面提示 */
    int deleteBySession(Long sessionId);
}
