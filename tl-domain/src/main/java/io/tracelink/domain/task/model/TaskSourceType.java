package io.tracelink.domain.task.model;

/** 工单来源。会话不产出草稿，因此没有会话来源（Q12） */
public enum TaskSourceType {

    MANUAL("手工"),
    REPORT("日报导入"),
    CHAT("会话");

    private final String label;

    TaskSourceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
