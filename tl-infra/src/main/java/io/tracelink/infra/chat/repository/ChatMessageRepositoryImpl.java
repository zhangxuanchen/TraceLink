package io.tracelink.infra.chat.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.chat.model.ChatMessage;
import io.tracelink.domain.chat.repository.ChatMessageRepository;
import io.tracelink.infra.chat.entity.ChatMessageEntity;
import io.tracelink.infra.chat.mapper.ChatMessageMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageMapper mapper;

    public ChatMessageRepositoryImpl(ChatMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ChatMessage message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.sessionId = message.sessionId;
        entity.role = message.role;
        entity.content = message.content;
        entity.citations = message.citations;
        entity.toolCalls = message.toolCalls;
        entity.modelName = message.modelName;
        entity.promptVer = message.promptVer;
        entity.tokenIn = message.tokenIn;
        entity.tokenOut = message.tokenOut;
        entity.latencyMs = message.latencyMs;
        entity.createdAt = message.createdAt == null ? LocalDateTime.now() : message.createdAt;
        mapper.insert(entity);
        message.id = entity.id;
        message.createdAt = entity.createdAt;
    }

    @Override
    public List<ChatMessage> listBySession(Long sessionId, int limit) {
        List<ChatMessageEntity> rows = mapper.selectList(new QueryWrapper<ChatMessageEntity>()
                .eq("session_id", sessionId)
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
        List<ChatMessage> ordered = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            ordered.add(toModel(rows.get(i)));
        }
        return ordered;
    }

    @Override
    public ChatMessage findBySessionAndContent(Long sessionId, String role, String content) {
        List<ChatMessageEntity> rows = mapper.selectList(new QueryWrapper<ChatMessageEntity>()
                .eq("session_id", sessionId)
                .eq("role", role)
                .eq("content", content)
                .orderByDesc("id")
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : toModel(rows.get(0));
    }

    @Override
    public int deleteBySession(Long sessionId) {
        return mapper.delete(new QueryWrapper<ChatMessageEntity>()
                .eq("session_id", sessionId));
    }

    public static ChatMessage toModel(ChatMessageEntity e) {
        ChatMessage m = new ChatMessage();
        m.id = e.id;
        m.sessionId = e.sessionId;
        m.role = e.role;
        m.content = e.content;
        m.citations = e.citations;
        m.toolCalls = e.toolCalls;
        m.modelName = e.modelName;
        m.promptVer = e.promptVer;
        m.tokenIn = e.tokenIn;
        m.tokenOut = e.tokenOut;
        m.latencyMs = e.latencyMs;
        m.createdAt = e.createdAt;
        return m;
    }
}
