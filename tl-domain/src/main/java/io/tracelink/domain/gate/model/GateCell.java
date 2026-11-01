package io.tracelink.domain.gate.model;

/** 单工单完成地图上的一行：条件状态、承担者、滞留天数与直达动作。 */
public class GateCell {

    public GateCode code;
    public String name;
    public GateState state;
    public Integer weight;
    public boolean required;
    public Long holderId;
    public String holderName;
    public int gapDays;
    public String actionUrl;
    public String hint;

    public static GateCell of(GateRule rule, GateState state, int gapDays) {
        GateCell cell = new GateCell();
        cell.code = rule.gateCode;
        cell.name = rule.gateCode.title();
        cell.state = state;
        cell.weight = rule.weightValue();
        cell.required = rule.requiredValue();
        cell.gapDays = gapDays;
        return cell;
    }
}
