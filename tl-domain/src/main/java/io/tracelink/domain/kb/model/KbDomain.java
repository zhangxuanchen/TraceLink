package io.tracelink.domain.kb.model;

/** 知识域。历史问题与措施排在最前，其次是制度、流程、客户与项目、会议汇报 */
public enum KbDomain {

    HISTORY("历史问题与措施"),
    POLICY("制度"),
    PROCESS("流程"),
    CUSTOMER("客户与项目"),
    MEETING("会议与汇报"),
    OTHER("其它");

    private final String label;

    KbDomain(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static KbDomain parse(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (KbDomain domain : values()) {
            if (domain.name().equalsIgnoreCase(value)) {
                return domain;
            }
        }
        return OTHER;
    }
}
