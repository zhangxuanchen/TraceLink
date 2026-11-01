package io.tracelink.domain.gate.model;

/** 九条完成条件。编码固定，名称与缺口承担角色可在 gate_rule 里改，这里只给默认值。 */
public enum GateCode {

    G1("已接单", HolderRole.PRIMARY, "/tasks/%d#dispatch"),
    G2("期限确定", HolderRole.OWNER, "/tasks/%d#reschedule"),
    G3("问题关闭", HolderRole.PRIMARY, "/tasks/%d#issues"),
    G4("措施执行", HolderRole.ACTION_OWNER, "/tasks/%d#actions"),
    G5("结果留痕", HolderRole.PRIMARY, "/tasks/%d#checkin"),
    G6("证据齐备", HolderRole.PRIMARY, "/tasks/%d#checkin"),
    G7("阻碍解除", HolderRole.OWNER, "/tasks/%d#blocker"),
    G8("指标达成", HolderRole.PRIMARY, "/tasks/%d#metrics"),
    G9("验收通过", HolderRole.OWNER, "/tasks/%d#verify");

    private final String title;
    private final HolderRole defaultHolder;
    private final String actionTemplate;

    GateCode(String title, HolderRole defaultHolder, String actionTemplate) {
        this.title = title;
        this.defaultHolder = defaultHolder;
        this.actionTemplate = actionTemplate;
    }

    public String title() {
        return title;
    }

    public HolderRole defaultHolder() {
        return defaultHolder;
    }

    public String actionUrl(Long taskId) {
        return String.format(actionTemplate, taskId);
    }
}
