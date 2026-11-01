package io.tracelink.domain.task.model;

/** 问题项状态。关闭要求带结论或措施，避免无声关闭 */
public enum IssueStatus {

    OPEN("未关闭"),
    CLOSED("已关闭");

    private final String label;

    IssueStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
