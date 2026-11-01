package io.tracelink.app.task;

import java.util.List;

/** 提交验收：结果说明必填，验收条件逐条判定 */
public record SubmitCommand(String resultSummary, List<Boolean> criteriaChecks) {
}
