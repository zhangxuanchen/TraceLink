package io.tracelink.domain.exporting.model;

public enum ExportStatus {

    PENDING("排队中"),
    RUNNING("生成中"),
    DONE("已完成"),
    FAILED("生成失败");

    private final String label;

    ExportStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
