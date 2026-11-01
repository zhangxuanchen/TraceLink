package io.tracelink.app.kb;

import java.time.LocalDateTime;

/** 解析与切片同步状态（KB-4） */
public record KbSyncView(
        Long id,
        String syncStatus,
        String syncStatusLabel,
        Integer chunkCnt,
        String syncMsg,
        LocalDateTime syncedAt,
        String retriever) {
}
