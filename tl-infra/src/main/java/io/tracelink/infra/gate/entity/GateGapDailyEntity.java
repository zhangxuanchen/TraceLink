package io.tracelink.infra.gate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.gate.model.GateCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("gate_gap_daily")
public class GateGapDailyEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public LocalDate snapDate;
    public Long deptId;
    public GateCode gateCode;
    public Integer doneCount;
    public Integer todoCount;
    public Integer naCount;
    public Integer maxGapDays;
    public LocalDateTime updatedAt;
}
