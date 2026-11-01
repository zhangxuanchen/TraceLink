package io.tracelink.infra.chat.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.chat.model.ChatSession;
import io.tracelink.domain.chat.model.ChatSessionStatus;
import io.tracelink.domain.chat.repository.ChatSessionRepository;
import io.tracelink.infra.chat.entity.ChatSessionEntity;
import io.tracelink.infra.chat.mapper.ChatSessionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ChatSessionRepositoryImpl implements ChatSessionRepository {

    private final ChatSessionMapper mapper;

    public ChatSessionRepositoryImpl(ChatSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ChatSession findById(Long id) {
        ChatSessionEntity entity = mapper.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.deleted)) {
            return null;
        }
        return toModel(entity);
    }

    @Override
    public List<ChatSession> listByEmployee(Long employeeId, int limit) {
        return mapper.selectList(new QueryWrapper<ChatSessionEntity>()
                        .eq("employee_id", employeeId)
                        .eq("deleted", 0)
                        .orderByDesc("last_msg_at")
                        .orderByDesc("id")
                        .last("LIMIT " + Math.max(1, Math.min(limit, 200)))
                ).stream().map(ChatSessionRepositoryImpl::toModel).toList();
    }

    @Override
    public void insert(ChatSession session) {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.employeeId = session.employeeId;
        entity.title = session.title;
        entity.scopeType = session.scopeType;
        entity.scopeId = session.scopeId;
        entity.msgCount = 0;
        entity.lastMsgAt = session.lastMsgAt;
        entity.status = session.status == null ? ChatSessionStatus.OPEN : session.status;
        entity.deleted = false;
        entity.createdAt = session.createdAt == null ? LocalDateTime.now() : session.createdAt;
        mapper.insert(entity);
        session.id = entity.id;
        session.createdAt = entity.createdAt;
    }

    @Override
    public void updateActivity(Long id, String title, int msgCount, LocalDateTime lastMsgAt) {
        UpdateWrapper<ChatSessionEntity> wrapper = new UpdateWrapper<ChatSessionEntity>()
                .eq("id", id)
                .set("msg_count", msgCount)
                .set("last_msg_at", lastMsgAt);
        if (title != null && !title.isBlank()) {
            wrapper.set("title", title);
        }
        mapper.update(null, wrapper);
    }

    @Override
    public void updateStatus(Long id, ChatSessionStatus status) {
        mapper.update(null, new UpdateWrapper<ChatSessionEntity>()
                .eq("id", id)
                .set("status", status.name()));
    }

    public static ChatSession toModel(ChatSessionEntity e) {
        ChatSession s = new ChatSession();
        s.id = e.id;
        s.employeeId = e.employeeId;
        s.title = e.title;
        s.scopeType = e.scopeType;
        s.scopeId = e.scopeId;
        s.msgCount = e.msgCount;
        s.lastMsgAt = e.lastMsgAt;
        s.status = e.status;
        s.deleted = e.deleted;
        s.createdAt = e.createdAt;
        return s;
    }
}
