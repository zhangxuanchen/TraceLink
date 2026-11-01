package io.tracelink.domain.task.model;

/** 关闭强度：强关闭有附件或验收确认，弱关闭只有一句话，未关闭为空 */
public enum ClosureLevel {

    STRONG("强关闭"),
    WEAK("弱关闭"),
    NONE("未关闭");

    private final String label;

    ClosureLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
