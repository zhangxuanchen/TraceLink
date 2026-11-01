package io.tracelink.domain.audit.model;

import java.time.LocalDateTime;

/** 审计记录。写操作全部留痕，不参与业务事务（DESIGN 6.5） */
public class AuditLog {

    public Long id;
    public Long actorId;
    public String action;
    public String bizType;
    public Long bizId;
    public String before;
    public String after;
    public String traceId;
    public LocalDateTime createdAt;
}
