package io.tracelink.infra.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.task.model.CheckinResultType;
import io.tracelink.domain.task.model.ClosureLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("task_checkin")
public class TaskCheckinEntity {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long taskId;
    public Long employeeId;
    public LocalDate checkDate;
    public Integer progress;
    public String content;
    public String blockerText;
    public String nextStep;
    public LocalDateTime planEndSnapshot;
    public CheckinResultType resultType;
    public ClosureLevel closureLevel;
    public LocalDateTime createdAt;
}
