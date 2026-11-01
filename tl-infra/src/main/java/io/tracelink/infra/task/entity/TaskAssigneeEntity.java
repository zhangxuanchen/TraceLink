package io.tracelink.infra.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.task.model.AssigneeRoleType;

import java.time.LocalDateTime;

@TableName("task_assignee")
public class TaskAssigneeEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long taskId;
    public Long employeeId;
    public AssigneeRoleType roleType;
    public LocalDateTime createdAt;
}
