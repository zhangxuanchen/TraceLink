package io.tracelink.app.eval;

/** 一次评价生成的结果：周期唯一，已存在的对象计入 skipped */
public record EvaluationRunView(
        String period,
        String from,
        String to,
        int employeeCreated,
        int taskCreated,
        int skipped,
        java.util.List<String> notes) {
}
