package io.tracelink.infra.kb.retriever;

import io.tracelink.domain.kb.model.KbDocument;
import io.tracelink.domain.kb.model.KbDomain;
import io.tracelink.domain.kb.model.KbScope;
import io.tracelink.domain.kb.model.KnowledgeHit;
import io.tracelink.domain.kb.repository.KbChunkRepository;
import io.tracelink.domain.kb.repository.KbDocumentRepository;
import io.tracelink.domain.kb.retriever.KnowledgeRetriever;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地全文检索：MySQL ngram 全文索引加 LIKE 兜底。
 * 只追求最坏情况下仍有东西可查，不追求召回率；结果标注为 LOCAL，页面提示这是降级结果。
 */
public class LocalKnowledgeRetriever implements KnowledgeRetriever {

    private static final double LIKE_SIMILARITY = 0.35;

    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;

    public LocalKnowledgeRetriever(KbDocumentRepository documentRepository, KbChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Override
    public List<KnowledgeHit> retrieve(String question, KbScope scope, KbDomain domain, int topK, double threshold) {
        List<Long> docIds = documentRepository.listReadableIds(scope.all(), scope.deptIds(), domain);
        if (docIds.isEmpty()) {
            return List.of();
        }
        List<KbChunkRepository.ChunkHit> rows = chunkRepository.searchFulltext(docIds, question, topK * 4);
        boolean like = false;
        if (rows.isEmpty()) {
            rows = chunkRepository.searchLike(docIds, question, topK * 4);
            like = true;
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        double maxScore = rows.stream().mapToDouble(KbChunkRepository.ChunkHit::score).max().orElse(1.0);
        Map<Long, KbDocument> docs = new HashMap<>();
        List<KnowledgeHit> hits = new ArrayList<>();
        for (KbChunkRepository.ChunkHit row : rows) {
            double similarity = like ? LIKE_SIMILARITY : normalize(row.score(), maxScore);
            if (similarity < threshold) {
                continue;
            }
            KbDocument doc = docs.computeIfAbsent(row.chunk().docId, documentRepository::findById);
            if (doc == null) {
                continue;
            }
            hits.add(new KnowledgeHit(doc.id, doc.title, doc.domain, doc.deptId, doc.visibility,
                    row.chunk().seq, row.chunk().id, row.chunk().externalChunkId,
                    row.chunk().content, row.chunk().pageNo, similarity, "LOCAL"));
        }
        hits.sort(Comparator.comparingDouble(KnowledgeHit::similarity).reversed());
        return hits.stream().limit(Math.max(1, topK)).toList();
    }

    private double normalize(double score, double maxScore) {
        if (maxScore <= 0) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.05, score / maxScore));
    }

    @Override
    public String name() {
        return "LOCAL";
    }
}
