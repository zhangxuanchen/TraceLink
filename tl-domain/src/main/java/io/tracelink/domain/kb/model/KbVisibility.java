package io.tracelink.domain.kb.model;

/** 文档可见范围。ALL 全员；DEPT 本部门；DEPT_TREE 本部门及下级 */
public enum KbVisibility {

    ALL("全员可见"),
    DEPT("本部门"),
    DEPT_TREE("本部门及下级");

    private final String label;

    KbVisibility(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static KbVisibility parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        for (KbVisibility v : values()) {
            if (v.name().equalsIgnoreCase(value)) {
                return v;
            }
        }
        return ALL;
    }
}
