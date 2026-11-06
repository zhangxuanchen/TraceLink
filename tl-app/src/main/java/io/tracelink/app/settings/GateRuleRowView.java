package io.tracelink.app.settings;

/** 一条完成条件：id 指向 gate_rule 行，改权重或停用都记录到该行 */
public record GateRuleRowView(
        Long id,
        String taskType,
        String gateCode,
        String gateLabel,
        int weight,
        boolean required,
        String holderRole,
        String holderRoleLabel,
        boolean enabled,
        boolean inherited) {
}
