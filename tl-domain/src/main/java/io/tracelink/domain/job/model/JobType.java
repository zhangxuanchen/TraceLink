package io.tracelink.domain.job.model;

/** 异步任务类型，与 DESIGN 6.2 的 job_task.job_type 对齐 */
public enum JobType {

    PARSE_IMPORT("导入解析"),
    EXPORT("导出生成"),
    EVAL("评价重算"),
    GATE_SNAPSHOT("完成地图快照"),
    SYNC_KB("知识库同步");

    private final String label;

    JobType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
