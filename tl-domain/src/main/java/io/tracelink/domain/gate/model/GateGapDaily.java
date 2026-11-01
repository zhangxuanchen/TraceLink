package io.tracelink.domain.gate.model;

import java.time.LocalDate;

/** 缺口按日按部门按条件的聚合计数，快照回收后仍可看趋势。 */
public class GateGapDaily {

    public Long id;
    public LocalDate snapDate;
    public Long deptId;
    public GateCode gateCode;
    public Integer doneCount;
    public Integer todoCount;
    public Integer naCount;
    public Integer maxGapDays;
}
