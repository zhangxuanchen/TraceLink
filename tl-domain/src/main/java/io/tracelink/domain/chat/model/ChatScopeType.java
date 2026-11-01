package io.tracelink.domain.chat.model;

/** 会话范围：与组织数据范围同名，创建时固化，避免部门调整后历史会话口径漂移 */
public enum ChatScopeType {

    SELF("仅本人"),
    DEPT("本部门"),
    DEPT_TREE("本部门及下级"),
    ALL("全公司");

    private final String label;

    ChatScopeType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ChatScopeType parse(String value) {
        if (value == null || value.isBlank()) {
            return DEPT;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEPT;
        }
    }
}
