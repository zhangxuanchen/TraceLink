package io.tracelink.domain.task.model;

import java.time.LocalDateTime;

/**
 * 工单主体。字段与 docs/DESIGN.md 6.2 的 task 表一一对应，这里用公有字段做纯数据载体，
 * 状态迁移规则集中在 rules/TaskStateMachine，不散落在实体方法里。
 */
public class Task {

    public Long id;
    public String taskNo;
    public String title;
    public String description;
    public TaskType type;
    public TaskSourceType sourceType;
    public String sourceRef;
    public Long deptId;
    public Long ownerId;
    public Long primaryAssigneeId;
    public String assigneeText;
    public Integer priority;
    public TaskStatus status;
    public LocalDateTime planStart;
    public LocalDateTime planEnd;
    public LocalDateTime actualEnd;
    public Integer progress;
    public String acceptCriteria;
    public String resultSummary;
    public Integer delayCount;
    public Integer rejectCount;
    public Integer urgeCount;
    public ClosureLevel closureLevel;
    public Long recurrenceOf;
    public Long parentId;
    public String tags;
    public boolean deleted;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public int progressValue() {
        return progress == null ? 0 : progress;
    }

    public int delayValue() {
        return delayCount == null ? 0 : delayCount;
    }

    public int rejectValue() {
        return rejectCount == null ? 0 : rejectCount;
    }

    public int urgeValue() {
        return urgeCount == null ? 0 : urgeCount;
    }
}
