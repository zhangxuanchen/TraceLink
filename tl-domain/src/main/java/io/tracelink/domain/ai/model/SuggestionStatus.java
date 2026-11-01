package io.tracelink.domain.ai.model;

/** 建议状态：待确认队列，人采纳后才执行动作（Q12） */
public enum SuggestionStatus {

    PENDING("待确认"),
    ACCEPTED("已采纳"),
    IGNORED("已忽略");

    private final String label;

    SuggestionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
