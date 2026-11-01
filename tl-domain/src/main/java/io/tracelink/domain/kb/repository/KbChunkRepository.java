package io.tracelink.domain.kb.repository;

import io.tracelink.domain.kb.model.KbChunk;

import java.util.List;

public interface KbChunkRepository {

    void insertBatch(List<KbChunk> chunks);

    /** 重新解析前把旧切片标为失效，不物理删除，历史引用仍可按 ID 取回原文 */
    int supersedeByDoc(Long docId);

    /** 按 ID 批量失效：新切片先写入成功后再调用，避免新片写失败时旧片已经消失 */
    int supersedeByIds(List<Long> ids);

    List<KbChunk> listByDoc(Long docId, boolean includeSuperseded);

    KbChunk findById(Long id);

    KbChunk findByExternalId(String externalChunkId);

    long countValidByDoc(Long docId);

    /** 本地全文检索（ngram），返回原始相关度；无命中时调用方再走 LIKE 兜底 */
    List<ChunkHit> searchFulltext(List<Long> docIds, String question, int limit);

    List<ChunkHit> searchLike(List<Long> docIds, String question, int limit);

    record ChunkHit(KbChunk chunk, double score) {
    }
}
