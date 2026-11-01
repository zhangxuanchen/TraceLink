package io.tracelink.app.task;

import java.time.LocalDateTime;
import java.util.List;

/** 派工：指定主责、期限与优先级，可同时加协办人 */
public record DispatchCommand(
        Long primaryAssigneeId,
        List<Long> coAssigneeIds,
        LocalDateTime planEnd,
        Integer priority,
        String note) {
}
