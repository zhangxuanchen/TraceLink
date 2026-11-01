package io.tracelink.domain.eval.model;

/** 评价状态：生成即发布，申诉只改状态并把理由写进证据 */
public enum EvaluationStatus {

    DRAFT("草稿"),
    PUBLISHED("已发布"),
    APPEALED("申诉中");

    private final String label;

    EvaluationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
