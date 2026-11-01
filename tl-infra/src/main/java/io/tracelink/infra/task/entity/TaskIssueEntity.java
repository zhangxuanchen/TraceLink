package io.tracelink.infra.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.task.model.IssueStatus;

import java.time.LocalDateTime;

@TableName("task_issue")
public class TaskIssueEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long taskId;
    public Integer seq;
    public String content;
    public String category;
    public Integer severity;
    public IssueStatus status;
    public LocalDateTime closedAt;
}
