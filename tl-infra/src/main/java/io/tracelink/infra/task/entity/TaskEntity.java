package io.tracelink.infra.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.task.model.ClosureLevel;
import io.tracelink.domain.task.model.TaskSourceType;
import io.tracelink.domain.task.model.TaskStatus;
import io.tracelink.domain.task.model.TaskType;

import java.time.LocalDateTime;

@TableName("task")
public class TaskEntity {

    @TableId(type = IdType.AUTO)
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
    public Boolean deleted;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
