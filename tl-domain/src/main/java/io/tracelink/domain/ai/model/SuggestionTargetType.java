package io.tracelink.domain.ai.model;

/** 建议对象 */
public enum SuggestionTargetType {

    TASK("工单"),
    DEPT("部门"),
    EMPLOYEE("人员");

    private final String label;

    SuggestionTargetType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SuggestionTargetType parse(String value) {
        if (value == null || value.isBlank()) {
            return TASK;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TASK;
        }
    }
}
