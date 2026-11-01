package io.tracelink.domain.task.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 追踪记录，时间线的唯一来源 */
public class TaskCheckin {

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
