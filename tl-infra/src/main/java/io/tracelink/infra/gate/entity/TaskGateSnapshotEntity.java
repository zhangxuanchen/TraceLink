package io.tracelink.infra.gate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.gate.model.GateCode;
import io.tracelink.domain.gate.model.GateState;

import java.time.LocalDate;

@TableName("task_gate_snapshot")
public class TaskGateSnapshotEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public LocalDate snapDate;
    public Long taskId;
    public Long deptId;
    public String taskType;
    public GateCode gateCode;
    public GateState state;
    public Integer gapDays;
    public Long holderId;
}
