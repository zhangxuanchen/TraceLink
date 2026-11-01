package io.tracelink.app.task;

import java.time.LocalDate;

/** 打卡与登记阻碍走同一条记录，resolvedBlocker 表示执行人自己恢复了阻碍 */
public record CheckinCommand(
        Integer progress,
        String content,
        String blockerText,
        String nextStep,
        String resultType,
        LocalDate checkDate,
        boolean resolvedBlocker) {
}
