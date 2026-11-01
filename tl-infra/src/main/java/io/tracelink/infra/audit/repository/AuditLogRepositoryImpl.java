package io.tracelink.infra.audit.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.infra.audit.entity.AuditLogEntity;
import io.tracelink.infra.audit.mapper.AuditLogMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogMapper auditLogMapper;

    public AuditLogRepositoryImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void insert(AuditLog log) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.actorId = log.actorId;
        entity.action = log.action;
        entity.bizType = log.bizType;
        entity.bizId = log.bizId;
        entity.before = log.before;
        entity.after = log.after;
        entity.traceId = log.traceId;
        auditLogMapper.insert(entity);
    }

    @Override
    public LocalDateTime lastActionAt(String bizType, Long bizId, String action) {
        AuditLogEntity entity = auditLogMapper.selectOne(new QueryWrapper<AuditLogEntity>()
                .eq("biz_type", bizType)
                .eq("biz_id", bizId)
                .eq("action", action)
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("LIMIT 1"));
        return entity == null ? null : entity.createdAt;
    }
}
