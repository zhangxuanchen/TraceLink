package io.tracelink.app.gate;

/** 单工单完成地图上的一条条件。字段与 CMP-1 契约一致。 */
public record GateItem(
        String code,
        String name,
        String state,
        String stateLabel,
        int weight,
        boolean required,
        Long holderId,
        String holderName,
        int gapDays,
        String actionUrl,
        String hint) {
}
