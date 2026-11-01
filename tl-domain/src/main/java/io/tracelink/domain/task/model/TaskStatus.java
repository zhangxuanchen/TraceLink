package io.tracelink.domain.task.model;

/**
 * 工单状态。逾期是标记不是状态，因为逾期工单仍要保留它原本处于哪个阶段（DESIGN 4.2）。
 */
public enum TaskStatus {

    DRAFT("草稿"),
    PENDING("待接单"),
    IN_PROGRESS("进行中"),
    BLOCKED("阻碍中"),
    SUBMITTED("待验收"),
    DONE("已完成"),
    CANCELLED("已取消");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 未结案：仍会出现在在办列表与逾期池里 */
    public boolean isOpen() {
        return this == PENDING || this == IN_PROGRESS || this == BLOCKED || this == SUBMITTED;
    }

    /** 执行人可以打卡的阶段 */
    public boolean isWorkable() {
        return this == PENDING || this == IN_PROGRESS || this == BLOCKED;
    }
}
