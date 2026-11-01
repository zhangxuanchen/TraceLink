package io.tracelink.app.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 建单入参，字段与 DESIGN 5.2 的 TSK-1 契约对齐 */
public record TaskCreateCommand(
        String title,
        String description,
        String type,
        String sourceType,
        Long deptId,
        Long ownerId,
        Long primaryAssigneeId,
        List<Long> coAssigneeIds,
        String assigneeText,
        Integer priority,
        LocalDateTime planStart,
        LocalDateTime planEnd,
        List<String> acceptCriteria,
        List<String> issueList,
        List<TaskActionInput> actionList,
        String tags) {

    public record TaskActionInput(String content, Long ownerId, LocalDate dueDate) {
    }
}
