package io.tracelink.domain.audit.repository;

import java.time.LocalDate;

/** 审计查询条件。before/after 是 JSON 原文，页面按对象与按人两个视图筛 */
public record AuditLogQuery(
        String bizType,
        Long bizId,
        Long actorId,
        String action,
        LocalDate from,
        LocalDate to,
        int pageNo,
        int pageSize) {
}
