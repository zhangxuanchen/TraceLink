package io.tracelink.domain.kb.retriever;

import io.tracelink.domain.kb.model.KbDomain;
import io.tracelink.domain.kb.model.KbScope;
import io.tracelink.domain.kb.model.KnowledgeHit;

import java.util.List;

/**
 * 检索端口。RAGFlow 可用时走向量加标签过滤，不可用时走本地全文；
 * 后置复核（切片归属与数据范围）由调用方完成，适配器只负责召回。
 */
public interface KnowledgeRetriever {

    List<KnowledgeHit> retrieve(String question, KbScope scope, KbDomain domain, int topK, double threshold);

    /** LOCAL / RAGFLOW / RAGFLOW_DEGRADED，供页面标注是否降级结果 */
    String name();
}
