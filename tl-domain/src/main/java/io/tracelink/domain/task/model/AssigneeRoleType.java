package io.tracelink.domain.task.model;

/** 参与人类型：协办承担部分工作，配合只接收信息 */
public enum AssigneeRoleType {

    ASSIST("协办"),
    COOPERATE("配合");

    private final String label;

    AssigneeRoleType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
