package io.tracelink.infra.audit.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.tracelink.common.api.PageResult;
import io.tracelink.domain.audit.model.AuditLog;
import io.tracelink.domain.audit.repository.AuditLogQuery;
import io.tracelink.domain.audit.repository.AuditLogRepository;
import io.tracelink.infra.audit.entity.AuditLogEntity;
import io.tracelink.infra.audit.mapper.AuditLogMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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

    @Override
    public PageResult<AuditLog> page(AuditLogQuery query) {
        QueryWrapper<AuditLogEntity> wrapper = new QueryWrapper<AuditLogEntity>()
                .eq(query.bizType() != null && !query.bizType().isBlank(), "biz_type", query.bizType())
                .eq(query.bizId() != null, "biz_id", query.bizId())
                .eq(query.actorId() != null, "actor_id", query.actorId())
                .eq(query.action() != null && !query.action().isBlank(), "action", query.action())
                .ge(query.from() != null, "created_at", query.from() == null ? null : query.from().atStartOfDay())
                .lt(query.to() != null, "created_at",
                        query.to() == null ? null : query.to().plusDays(1).atStartOfDay())
                .orderByDesc("id");
        Page<AuditLogEntity> page = auditLogMapper.selectPage(
                new Page<>(query.pageNo(), query.pageSize()), wrapper);
        List<AuditLog> records = page.getRecords().stream().map(AuditLogRepositoryImpl::toModel).toList();
        return new PageResult<>(page.getTotal(), query.pageNo(), query.pageSize(), records);
    }

    public static AuditLog toModel(AuditLogEntity entity) {
        AuditLog log = new AuditLog();
        log.id = entity.id;
        log.actorId = entity.actorId;
        log.action = entity.action;
        log.bizType = entity.bizType;
        log.bizId = entity.bizId;
        log.before = entity.before;
        log.after = entity.after;
        log.traceId = entity.traceId;
        log.createdAt = entity.createdAt;
        return log;
    }
}
