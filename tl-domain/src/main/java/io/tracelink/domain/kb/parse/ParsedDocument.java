package io.tracelink.domain.kb.parse;

import java.util.List;

/** 解析结果：切片正文与页码，seq 由入库时统一编号。本地解析与远端解析共用这一形状 */
public record ParsedDocument(List<ParsedChunk> chunks, String note) {

    public record ParsedChunk(String content, Integer pageNo) {
    }

    public int size() {
        return chunks.size();
    }
}
