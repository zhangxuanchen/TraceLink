package io.tracelink.domain.audit.repository;

import io.tracelink.domain.audit.model.AuditLog;

import java.time.LocalDateTime;

public interface AuditLogRepository {

    void insert(AuditLog log);

    /** 最近一次指定动作的时间，催办二十四小时内限一次用它判断 */
    LocalDateTime lastActionAt(String bizType, Long bizId, String action);
}
