package io.tracelink.domain.task.model;

/** 追踪记录的类型。R3 要求提交前至少一条 RESULT */
public enum CheckinResultType {

    PROGRESS("进展"),
    RESULT("结果"),
    ISSUE("问题"),
    REVIEW("验收意见");

    private final String label;

    CheckinResultType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
