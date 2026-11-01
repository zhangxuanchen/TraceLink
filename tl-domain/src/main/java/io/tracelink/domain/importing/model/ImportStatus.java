package io.tracelink.domain.importing.model;

/** 批次状态。上传即入库，解析惰性触发，确认后进入 CONFIRMED */
public enum ImportStatus {

    UPLOADED("待解析"),
    ANALYZING("识别中"),
    PREVIEWED("待确认"),
    CONFIRMED("已入库"),
    FAILED("解析失败"),
    REVOKED("已撤回");

    private final String label;

    ImportStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
