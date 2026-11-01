package io.tracelink.domain.gate.model;

/** 缺口默认承担角色。具体人由工单与措施上的字段解析。 */
public enum HolderRole {

    PRIMARY("主责执行人"),
    OWNER("负责人"),
    ACTION_OWNER("措施执行人");

    private final String label;

    HolderRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static HolderRole parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return HolderRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
