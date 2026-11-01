package io.tracelink.infra.gate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.gate.model.GateCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("notification")
public class NotificationEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long recipientId;
    public String type;
    public Long taskId;
    public String taskNo;
    public GateCode gateCode;
    public String level;
    public Integer gapDays;
    public LocalDate episodeStart;
    public String title;
    public String content;
    public Boolean readFlag;
    public LocalDateTime createdAt;
}
