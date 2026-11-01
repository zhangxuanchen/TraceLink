package io.tracelink.domain.gate.model;

/** 一条完成条件口径：按工单类型配置权重、是否必达与缺口承担角色。 */
public class GateRule {

    public Long id;
    public String taskType;
    public GateCode gateCode;
    public Integer weight;
    public Boolean required;
    public HolderRole holderRole;
    public Integer sortNo;
    public Boolean enabled;

    public int weightValue() {
        return weight == null ? 0 : weight;
    }

    public boolean requiredValue() {
        return Boolean.TRUE.equals(required);
    }

    public boolean enabledValue() {
        return enabled == null || enabled;
    }
}
