package io.tracelink.domain.exporting.port;

import io.tracelink.domain.exporting.model.ExportWorkbook;

/** 工作簿写出端口。POI 细节留在 tl-infra，应用层只拼数据 */
public interface WorkbookWriter {

    byte[] write(ExportWorkbook workbook);
}
