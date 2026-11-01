package io.tracelink.infra.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long actorId;
    public String action;
    public String bizType;
    public Long bizId;
    @TableField("`before`")
    public String before;
    @TableField("`after`")
    public String after;
    public String traceId;
    public LocalDateTime createdAt;
}
