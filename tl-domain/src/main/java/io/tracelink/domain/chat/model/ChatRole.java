package io.tracelink.domain.chat.model;

/** 消息角色：用户、助手、工具回包。工具轮不显示为气泡 */
public enum ChatRole {

    USER("提问"),
    ASSISTANT("回答"),
    TOOL("工具");

    private final String label;

    ChatRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
