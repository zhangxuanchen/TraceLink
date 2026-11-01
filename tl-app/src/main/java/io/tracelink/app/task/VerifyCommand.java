package io.tracelink.app.task;

import java.util.List;

/** 验收：PASS 或 REJECT，退回必须写理由 */
public record VerifyCommand(
        String decision,
        String resultSummary,
        String rejectReason,
        List<Boolean> criteriaChecks) {
}
