package io.tracelink.domain.job.model;

/** 任务状态。PENDING 排队、RUNNING 执行中，两者都可被页面轮询到 */
public enum JobStatus {

    PENDING("排队中"),
    RUNNING("执行中"),
    DONE("已完成"),
    FAILED("失败");

    private final String label;

    JobStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean finished() {
        return this == DONE || this == FAILED;
    }
}
