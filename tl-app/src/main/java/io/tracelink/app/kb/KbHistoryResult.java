package io.tracelink.app.kb;

/** 历史问题与措施沉淀结果（KB-6） */
public record KbHistoryResult(int scanned, int created, int skipped, java.util.List<Long> documentIds) {
}
