package io.tracelink.app.chat;

/** 会话问答入参（CHT-3），scope 不传取本部门；allowTools 关闭时只走知识检索 */
public record ChatAskCommand(String question, Long deptId, java.util.List<String> taskTypes, Boolean allowTools) {

    public boolean toolsAllowed() {
        return allowTools == null || allowTools;
    }
}
