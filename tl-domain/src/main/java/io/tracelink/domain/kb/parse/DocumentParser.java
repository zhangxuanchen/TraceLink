package io.tracelink.domain.kb.parse;

import java.nio.file.Path;

/** 文档解析端口：RAGFlow 不可用时由本地解析器实现，应用层只认这个接口 */
public interface DocumentParser {

    ParsedDocument parse(Path file, String fileName);
}
