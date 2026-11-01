package io.tracelink.domain.task.model;

/** 改期原因四分类（DESIGN 9.4.5） */
public enum RescheduleReason {

    EXTERNAL("等外部支持"),
    CHANGE("需求变更"),
    STAFFING("人手不足"),
    ESTIMATE("估算偏差");

    private final String label;

    RescheduleReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
