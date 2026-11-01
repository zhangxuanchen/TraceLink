package io.tracelink.domain.eval.model;

/** 评价对象：人员评价与任务复盘共用一张表，用 target_type 区分（DESIGN 4.8、6.2） */
public enum EvaluationTargetType {

    EMPLOYEE("人员"),
    TASK("工单");

    private final String label;

    EvaluationTargetType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static EvaluationTargetType parse(String value) {
        if (value == null || value.isBlank()) {
            return EMPLOYEE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EMPLOYEE;
        }
    }
}
