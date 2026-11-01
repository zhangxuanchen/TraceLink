package io.tracelink.domain.task.model;

import java.time.LocalDateTime;

/** 问题项，逐条可关，是完成条件 G3 的判定单位 */
public class TaskIssue {

    public Long id;
    public Long taskId;
    public Integer seq;
    public String content;
    public String category;
    public Integer severity;
    public IssueStatus status;
    public LocalDateTime closedAt;
}
