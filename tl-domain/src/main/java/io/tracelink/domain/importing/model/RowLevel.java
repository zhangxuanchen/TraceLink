package io.tracelink.domain.importing.model;

/** 行级校验档位。ERROR 不参与建单，WARN 建单但需人工复核 */
public enum RowLevel {

    OK("通过"),
    WARN("警告"),
    ERROR("错误");

    private final String label;

    RowLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
