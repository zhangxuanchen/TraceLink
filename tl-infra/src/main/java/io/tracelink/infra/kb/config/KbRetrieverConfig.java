package io.tracelink.infra.kb.config;

import io.tracelink.domain.kb.repository.KbChunkRepository;
import io.tracelink.domain.kb.repository.KbDocumentRepository;
import io.tracelink.domain.kb.retriever.KnowledgeRetriever;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.infra.kb.ragflow.RagFlowClient;
import io.tracelink.infra.kb.retriever.LocalKnowledgeRetriever;
import io.tracelink.infra.kb.retriever.RagFlowKnowledgeRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 检索实现只换一个 Bean：RAGFlow 配好且开关打开时走向量检索，否则走本地全文。
 * 带范围条件的检索统一经过范围层，不直接用 AgentScope 的 RAGFlowKnowledge。
 */
@Configuration
public class KbRetrieverConfig {

    @Bean
    public KnowledgeRetriever knowledgeRetriever(KbDocumentRepository documentRepository,
                                                 KbChunkRepository chunkRepository,
                                                 DeptRepository deptRepository,
                                                 RagFlowClient ragFlowClient,
                                                 @Value("${tracelink.ai.ragflow.enabled:false}") boolean enabled,
                                                 @Value("${tracelink.ai.ragflow.dataset-id:default}") String datasetId,
                                                 @Value("${tracelink.ai.ragflow.degrade-on-error:LOCAL_FULLTEXT}") String degrade) {
        LocalKnowledgeRetriever local = new LocalKnowledgeRetriever(documentRepository, chunkRepository);
        if (enabled && ragFlowClient.configured()) {
            boolean degradeOnError = "LOCAL_FULLTEXT".equalsIgnoreCase(degrade);
            return new RagFlowKnowledgeRetriever(ragFlowClient, documentRepository, chunkRepository,
                    deptRepository, local, datasetId, degradeOnError);
        }
        return local;
    }
}
