package io.tracelink.domain.kb.parse;

/** 解析失败统一类型，应用层据此把文档置为 FAILED 并写原因 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }
}
