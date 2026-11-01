package io.tracelink.domain.kb.model;

/** 检索命中。similarity 由适配器归一，retriever 标明来自 RAGFLOW 还是本地全文降级 */
public record KnowledgeHit(
        Long docId,
        String docTitle,
        KbDomain domain,
        Long deptId,
        KbVisibility visibility,
        Integer seq,
        Long chunkId,
        String externalChunkId,
        String content,
        Integer pageNo,
        double similarity,
        String retriever) {
}
