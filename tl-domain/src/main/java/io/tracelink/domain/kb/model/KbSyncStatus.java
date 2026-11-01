package io.tracelink.domain.kb.model;

/** 文档同步状态。上传后 PENDING，解析中 PARSING，成功 READY，失败 FAILED 可单篇重试 */
public enum KbSyncStatus {

    PENDING("待解析"),
    PARSING("解析中"),
    READY("已就绪"),
    FAILED("解析失败");

    private final String label;

    KbSyncStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static KbSyncStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        for (KbSyncStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return PENDING;
    }
}
