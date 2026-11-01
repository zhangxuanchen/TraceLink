package io.tracelink.domain.kb.repository;

import io.tracelink.domain.kb.model.KbDocument;
import io.tracelink.domain.kb.model.KbDomain;
import io.tracelink.domain.kb.model.KbSyncStatus;

import java.util.List;

public interface KbDocumentRepository {

    KbDocument findById(Long id);

    KbDocument findByHash(String fileHash);

    List<KbDocument> list(KbDomain domain, Long deptId, KbSyncStatus status, String keyword, int limit);

    /** 检索前置过滤：数据范围内可参与的文档 ID；all 为真不按部门收窄 */
    List<Long> listReadableIds(boolean all, List<Long> deptIds, KbDomain domain);

    void insert(KbDocument document);

    /** 只更新同步状态相关字段，避免整行覆盖 */
    void updateSync(Long id, KbSyncStatus status, String syncMsg, Integer chunkCnt,
                    String externalDocId, java.time.LocalDateTime syncedAt);
}
