package io.tracelink.infra.gate.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.gate.model.GateCode;
import io.tracelink.domain.gate.model.Notification;
import io.tracelink.domain.gate.repository.NotificationRepository;
import io.tracelink.infra.gate.entity.NotificationEntity;
import io.tracelink.infra.gate.mapper.NotificationMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationMapper mapper;

    public NotificationRepositoryImpl(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean exists(Long taskId, GateCode gateCode, String level, LocalDate episodeStart,
                          Long recipientId) {
        return mapper.selectCount(new QueryWrapper<NotificationEntity>()
                .eq("task_id", taskId)
                .eq("gate_code", gateCode.name())
                .eq("level", level)
                .eq("episode_start", episodeStart)
                .eq("recipient_id", recipientId)) > 0;
    }

    @Override
    public void insert(Notification notification) {
        NotificationEntity entity = new NotificationEntity();
        entity.recipientId = notification.recipientId;
        entity.type = notification.type == null ? "GAP" : notification.type;
        entity.taskId = notification.taskId;
        entity.taskNo = notification.taskNo;
        entity.gateCode = notification.gateCode;
        entity.level = notification.level;
        entity.gapDays = notification.gapDays;
        entity.episodeStart = notification.episodeStart;
        entity.title = notification.title;
        entity.content = notification.content;
        entity.readFlag = false;
        mapper.insert(entity);
        notification.id = entity.id;
    }

    @Override
    public List<Notification> listByRecipient(Long recipientId, boolean unreadOnly, int limit) {
        QueryWrapper<NotificationEntity> wrapper = new QueryWrapper<NotificationEntity>()
                .eq("recipient_id", recipientId)
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, limit));
        if (unreadOnly) {
            wrapper.eq("read_flag", 0);
        }
        return mapper.selectList(wrapper).stream().map(NotificationRepositoryImpl::toModel).toList();
    }

    @Override
    public boolean markRead(Long id, Long recipientId) {
        return mapper.update(null, new UpdateWrapper<NotificationEntity>()
                .eq("id", id)
                .eq("recipient_id", recipientId)
                .set("read_flag", 1)) > 0;
    }

    @Override
    public long countUnread(Long recipientId) {
        return mapper.selectCount(new QueryWrapper<NotificationEntity>()
                .eq("recipient_id", recipientId)
                .eq("read_flag", 0));
    }

    public static Notification toModel(NotificationEntity e) {
        Notification n = new Notification();
        n.id = e.id;
        n.recipientId = e.recipientId;
        n.type = e.type;
        n.taskId = e.taskId;
        n.taskNo = e.taskNo;
        n.gateCode = e.gateCode;
        n.level = e.level;
        n.gapDays = e.gapDays;
        n.episodeStart = e.episodeStart;
        n.title = e.title;
        n.content = e.content;
        n.readFlag = e.readFlag;
        n.createdAt = e.createdAt;
        return n;
    }
}
