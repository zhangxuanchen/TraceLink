package io.tracelink.app.kb;

import java.time.LocalDateTime;

/** 引用回溯：即便切片已被重新解析替换，也能按 ID 取到原快照 */
public record KbChunkView(
        Long chunkId,
        Long docId,
        String docTitle,
        Integer seq,
        Integer pageNo,
        String content,
        boolean superseded,
        LocalDateTime createdAt) {
}
