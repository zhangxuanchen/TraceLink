package io.tracelink.domain.task.model;

/** 参与人。只记参与，不承担验收与逾期归属 */
public class TaskAssignee {

    public Long id;
    public Long taskId;
    public Long employeeId;
    public AssigneeRoleType roleType;
}
