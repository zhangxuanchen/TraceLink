package io.tracelink.app.task;

import java.time.LocalDateTime;

/** 改期：旧期限用于并发校验，rootCause 与 measure 在超过次数上限时必填 */
public record RescheduleCommand(
        LocalDateTime newPlanEnd,
        LocalDateTime oldPlanEnd,
        String reason,
        String reasonText,
        String rootCause,
        String measure) {
}
