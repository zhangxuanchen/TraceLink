package io.tracelink.infra.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.task.model.ActionStatus;

import java.time.LocalDate;

@TableName("task_action")
public class TaskActionEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long taskId;
    public Long issueId;
    public Integer seq;
    public String content;
    public Long ownerId;
    public LocalDate dueDate;
    public ActionStatus status;
    public Long taskChildId;
}
