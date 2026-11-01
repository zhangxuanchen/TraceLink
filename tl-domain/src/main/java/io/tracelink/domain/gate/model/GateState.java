package io.tracelink.domain.gate.model;

/** 条件的三种状态。不适用与未达成分开存，避免矩阵把不适用算成缺口。 */
public enum GateState {

    DONE("达成"),
    TODO("未达成"),
    NA("不适用");

    private final String label;

    GateState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
