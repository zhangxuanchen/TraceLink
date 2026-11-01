package io.tracelink.domain.importing.port;

import java.nio.file.Path;

/** 工作簿解析端口。实现放 tl-infra，领域层只认这个契约 */
public interface WorkbookParser {

    ParsedWorkbook parse(Path file);
}
