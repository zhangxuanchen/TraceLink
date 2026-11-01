package io.tracelink.infra.kb.retriever;

import io.tracelink.common.error.BizException;
import io.tracelink.domain.kb.model.KbDocument;
import io.tracelink.domain.kb.model.KbDomain;
import io.tracelink.domain.kb.model.KbScope;
import io.tracelink.domain.kb.model.KnowledgeHit;
import io.tracelink.domain.kb.remote.KbRemotePort;
import io.tracelink.domain.kb.repository.KbChunkRepository;
import io.tracelink.domain.kb.repository.KbDocumentRepository;
import io.tracelink.domain.kb.retriever.KnowledgeRetriever;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.infra.kb.ragflow.RagFlowClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 前置按数据范围拼 metadata_condition 收窄召回池，后置按本地切片复核归属，
 * 两道过滤都做，防止标签写错时泄露；RAGFlow 不可达时按配置降级到本地全文。
 */
public class RagFlowKnowledgeRetriever implements KnowledgeRetriever {

    private final RagFlowClient client;
    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;
    private final DeptRepository deptRepository;
    private final LocalKnowledgeRetriever local;
    private final String datasetId;
    private final boolean degrade;

    public RagFlowKnowledgeRetriever(RagFlowClient client, KbDocumentRepository documentRepository,
                                     KbChunkRepository chunkRepository, DeptRepository deptRepository,
                                     LocalKnowledgeRetriever local, String datasetId, boolean degrade) {
        this.client = client;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.deptRepository = deptRepository;
        this.local = local;
        this.datasetId = datasetId;
        this.degrade = degrade;
    }

    @Override
    public List<KnowledgeHit> retrieve(String question, KbScope scope, KbDomain domain, int topK, double threshold) {
        if (!client.configured()) {
            return local.retrieve(question, scope, domain, topK, threshold);
        }
        try {
            List<Long> readableIds = documentRepository.listReadableIds(scope.all(), scope.deptIds(), domain);
            if (readableIds.isEmpty()) {
                return List.of();
            }
            List<KbRemotePort.RemoteChunk> remote = client.retrieve(datasetId, question, topK, threshold,
                    metadataCondition(scope));
            Map<Long, KbDocument> docs = new HashMap<>();
            List<KnowledgeHit> hits = new ArrayList<>();
            for (KbRemotePort.RemoteChunk chunk : remote) {
                io.tracelink.domain.kb.model.KbChunk localChunk = chunkRepository.findByExternalId(chunk.id());
                if (localChunk == null || !readableIds.contains(localChunk.docId)) {
                    continue;
                }
                KbDocument doc = docs.computeIfAbsent(localChunk.docId, documentRepository::findById);
                if (doc == null) {
                    continue;
                }
                double similarity = chunk.similarity() == null ? 0.0 : chunk.similarity();
                if (similarity < threshold) {
                    continue;
                }
                hits.add(new KnowledgeHit(doc.id, doc.title, doc.domain, doc.deptId, doc.visibility,
                        localChunk.seq, localChunk.id, localChunk.externalChunkId, localChunk.content,
                        localChunk.pageNo, similarity, "RAGFLOW"));
            }
            return hits.stream().limit(Math.max(1, topK)).toList();
        } catch (BizException e) {
            if (degrade) {
                return local.retrieve(question, scope, domain, topK, threshold);
            }
            throw e;
        }
    }

    /** 文档同步时把 dept_code 与 visibility 写进 RAGFlow 元数据，这里按同一口径拼条件 */
    private Map<String, Object> metadataCondition(KbScope scope) {
        if (scope.all()) {
            return Map.of();
        }
        List<Long> deptIds = scope.deptIds() == null ? List.of() : scope.deptIds();
        List<String> codes = deptRepository.findAll().stream()
                .filter(d -> deptIds.contains(d.getId()))
                .map(Dept::getCode)
                .filter(code -> code != null && !code.isBlank())
                .toList();
        List<Map<String, Object>> conditions = new ArrayList<>();
        conditions.add(Map.of("name", "visibility", "op", "eq", "value", "ALL"));
        if (!codes.isEmpty()) {
            conditions.add(Map.of("name", "dept_code", "op", "in", "value", codes));
        }
        return Map.of("op", "and", "conditions",
                List.of(Map.of("op", "or", "conditions", conditions)));
    }

    @Override
    public String name() {
        return "RAGFLOW";
    }
}
