package io.tracelink.domain.kb.remote;

import java.io.InputStream;
import java.util.List;

/**
 * 远端知识库端口：上传原件、触发解析、查状态、拉切片由 RAGFlow 承担。
 * 带范围条件的检索不走这里，由 KnowledgeRetriever 的范围层单独拼条件。
 */
public interface KbRemotePort {

    boolean configured();

    String uploadDocument(String datasetId, String fileName, InputStream content, String title);

    void triggerParse(String datasetId, String documentId);

    ParseState status(String datasetId, String documentId);

    List<RemoteChunk> chunks(String datasetId, String documentId);

    /** RAGFlow 文档解析状态：run 取 UNSTART / RUNNING / DONE / FAIL / CANCEL */
    record ParseState(String run, double progress, String message) {

        public boolean done() {
            return "DONE".equalsIgnoreCase(run);
        }

        public boolean failed() {
            return "FAIL".equalsIgnoreCase(run) || "CANCEL".equalsIgnoreCase(run);
        }
    }

    record RemoteChunk(String id, String documentId, String content, Double similarity,
                       Integer seq, Integer pageNo) {
    }
}
