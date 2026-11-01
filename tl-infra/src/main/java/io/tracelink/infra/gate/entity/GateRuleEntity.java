package io.tracelink.infra.gate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.gate.model.GateCode;
import io.tracelink.domain.gate.model.HolderRole;

@TableName("gate_rule")
public class GateRuleEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public String taskType;
    public GateCode gateCode;
    public Integer weight;
    public Boolean required;
    public HolderRole holderRole;
    public Integer sortNo;
    public Boolean enabled;
}
