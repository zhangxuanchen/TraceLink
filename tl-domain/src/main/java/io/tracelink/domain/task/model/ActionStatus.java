package io.tracelink.domain.task.model;

/** 措施项状态，G4 按它算执行率 */
public enum ActionStatus {

    OPEN("未完成"),
    DONE("已完成");

    private final String label;

    ActionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
