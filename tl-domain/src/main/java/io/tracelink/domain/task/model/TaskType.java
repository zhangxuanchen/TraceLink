package io.tracelink.domain.task.model;

/** 工单类型，决定用哪套完成条件口径（M3 读 gate_rule） */
public enum TaskType {

    PATROL("现场巡查"),
    QUALITY("质量"),
    PLAN("计划"),
    EQUIP("设备"),
    CUSTOMER("客户"),
    SAFETY("安全"),
    OTHER("其他");

    private final String label;

    TaskType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
