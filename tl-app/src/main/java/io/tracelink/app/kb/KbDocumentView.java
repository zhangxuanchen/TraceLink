package io.tracelink.app.kb;

import java.time.LocalDateTime;

/** 文档列表与详情展示 */
public record KbDocumentView(
        Long id,
        String title,
        String domain,
        String domainLabel,
        Long deptId,
        String deptName,
        String deptCode,
        String visibility,
        String visibilityLabel,
        String sourceType,
        String sourceRef,
        Integer chunkCnt,
        String syncStatus,
        String syncStatusLabel,
        String syncMsg,
        LocalDateTime syncedAt,
        LocalDateTime createdAt) {
}
