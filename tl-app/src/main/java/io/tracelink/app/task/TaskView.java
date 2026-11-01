package io.tracelink.app.task;

import java.time.LocalDateTime;

/** 工单列表行。actions 由后端按角色与状态算好，前端不用自己判断按钮该不该出现 */
public record TaskView(
        Long id,
        String taskNo,
        String title,
        String type,
        String typeLabel,
        String status,
        String statusLabel,
        Long deptId,
        String deptName,
        Long ownerId,
        String ownerName,
        Long primaryAssigneeId,
        String primaryAssigneeName,
        String assigneeText,
        Integer priority,
        LocalDateTime planStart,
        LocalDateTime planEnd,
        LocalDateTime actualEnd,
        Integer progress,
        boolean overdue,
        int overdueDays,
        String overdueLevel,
        String overdueLevelLabel,
        Long recurrenceOf,
        Integer delayCount,
        Integer rejectCount,
        Integer urgeCount,
        String closureLevel,
        LocalDateTime createdAt,
        Actions actions) {

    public record Actions(
            boolean canDispatch,
            boolean canCheckin,
            boolean canSubmit,
            boolean canVerify,
            boolean canReschedule,
            boolean canUrge,
            boolean canCancel) {
    }
}
