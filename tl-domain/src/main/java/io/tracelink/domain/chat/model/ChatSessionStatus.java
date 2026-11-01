package io.tracelink.domain.chat.model;

/** 会话状态：打开或归档。归档移出列表但保留引用与证据 */
public enum ChatSessionStatus {

    OPEN("打开"),
    ARCHIVED("已归档");

    private final String label;

    ChatSessionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ChatSessionStatus parse(String value) {
        return value != null && "ARCHIVED".equalsIgnoreCase(value) ? ARCHIVED : OPEN;
    }
}
