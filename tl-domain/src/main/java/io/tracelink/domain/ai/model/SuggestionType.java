package io.tracelink.domain.ai.model;

/** 五类建议（DESIGN 4.8）：采纳由应用服务执行确定性动作，模型不持有写接口 */
public enum SuggestionType {

    DISPATCH("派工建议"),
    ACTION("措施建议"),
    DUE_DATE("期限建议"),
    ESCALATE("升级建议"),
    POLICY_GAP("制度缺口建议");

    private final String label;

    SuggestionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SuggestionType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
