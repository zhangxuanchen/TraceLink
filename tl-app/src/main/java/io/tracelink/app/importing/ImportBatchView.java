package io.tracelink.app.importing;

/** 批次卡片。duplicate=true 表示同内容文件已上传过，直接复用旧批次 */
public record ImportBatchView(Long id, String fileName, String status, String statusLabel,
                              Integer sheetTotal, Integer rowTotal, Integer rowOk, Integer rowWarn,
                              Integer rowError, String createdAt, String errorMsg, boolean duplicate,
                              String signature) {
}
