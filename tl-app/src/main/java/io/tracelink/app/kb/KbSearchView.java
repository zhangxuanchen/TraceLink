package io.tracelink.app.kb;

import java.util.List;

/** 检索结果（KB-2），degraded 为真表示 RAGFlow 不可用走了本地全文 */
public record KbSearchView(
        String question,
        String retriever,
        boolean degraded,
        int total,
        List<Hit> hits) {

    public record Hit(
            Long docId,
            String docTitle,
            String domain,
            String domainLabel,
            Long deptId,
            String deptName,
            Integer seq,
            Long chunkId,
            String content,
            String excerpt,
            Integer pageNo,
            double similarity,
            String retriever) {
    }
}
