package io.tracelink.domain.importing.model;

/** 归并动作：新建、合并到已有工单（只追加一条追踪）、跳过 */
public enum RowAction {

    CREATE("新建"),
    MERGE("合并"),
    SKIP("跳过");

    private final String label;

    RowAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
