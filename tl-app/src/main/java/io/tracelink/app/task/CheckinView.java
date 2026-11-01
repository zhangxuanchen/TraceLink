package io.tracelink.app.task;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 时间线条目，按业务日期倒序 */
public record CheckinView(
        Long id,
        Long employeeId,
        String employeeName,
        LocalDate checkDate,
        Integer progress,
        String content,
        String blockerText,
        String nextStep,
        String resultType,
        String resultTypeLabel,
        String closureLevel,
        String closureLevelLabel,
        boolean backfill,
        LocalDateTime createdAt) {
}
