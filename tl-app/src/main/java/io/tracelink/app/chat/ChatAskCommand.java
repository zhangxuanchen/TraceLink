package io.tracelink.app.chat;

/** 会话问答入参（CHT-3）：deptId 只在外部调用时显式指定，页面不传；不传时由问题里的部门名自动收窄。allowTools 关闭时只走知识检索 */
public record ChatAskCommand(String question, Long deptId, java.util.List<String> taskTypes, Boolean allowTools) {

    public boolean toolsAllowed() {
        return allowTools == null || allowTools;
    }
}
