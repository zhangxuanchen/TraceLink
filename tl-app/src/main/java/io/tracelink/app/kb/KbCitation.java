package io.tracelink.app.kb;

/** 引用：存正文摘要，切片重建后仍可展示；chunkId 用于回溯原文 */
public record KbCitation(
        Long docId,
        Long chunkId,
        String docTitle,
        Integer seq,
        Integer pageNo,
        String excerpt) {
}
