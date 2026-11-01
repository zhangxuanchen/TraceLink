package io.tracelink.domain.task.model;

import java.time.LocalDate;

/** 措施项，带执行人与期限，是完成条件 G4 的判定单位 */
public class TaskAction {

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
