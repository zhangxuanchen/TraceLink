package io.tracelink.domain.gate.model;

import java.time.LocalDate;

/** 完成条件日快照的一行，唯一键为日期加工单加条件。 */
public class TaskGateSnapshot {

    public Long id;
    public LocalDate snapDate;
    public Long taskId;
    public Long deptId;
    public String taskType;
    public GateCode gateCode;
    public GateState state;
    public Integer gapDays;
    public Long holderId;

    public int gapValue() {
        return gapDays == null ? 0 : gapDays;
    }
}
