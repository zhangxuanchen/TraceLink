package io.tracelink.infra.kb.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.kb.model.KbDocument;
import io.tracelink.domain.kb.model.KbDomain;
import io.tracelink.domain.kb.model.KbSyncStatus;
import io.tracelink.domain.kb.repository.KbDocumentRepository;
import io.tracelink.infra.kb.entity.KbDocumentEntity;
import io.tracelink.infra.kb.mapper.KbDocumentMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class KbDocumentRepositoryImpl implements KbDocumentRepository {

    private final KbDocumentMapper mapper;

    public KbDocumentRepositoryImpl(KbDocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public KbDocument findById(Long id) {
        KbDocumentEntity entity = mapper.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.deleted)) {
            return null;
        }
        return toModel(entity);
    }

    @Override
    public KbDocument findByHash(String fileHash) {
        List<KbDocumentEntity> rows = mapper.selectList(new QueryWrapper<KbDocumentEntity>()
                .eq("file_hash", fileHash)
                .eq("deleted", 0)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : toModel(rows.get(0));
    }

    @Override
    public List<KbDocument> list(KbDomain domain, Long deptId, KbSyncStatus status, String keyword, int limit) {
        QueryWrapper<KbDocumentEntity> wrapper = new QueryWrapper<KbDocumentEntity>()
                .eq("deleted", 0)
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if (domain != null) {
            wrapper.eq("domain", domain.name());
        }
        if (deptId != null) {
            wrapper.eq("dept_id", deptId);
        }
        if (status != null) {
            wrapper.eq("sync_status", status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("title", keyword.trim());
        }
        return mapper.selectList(wrapper).stream().map(KbDocumentRepositoryImpl::toModel).toList();
    }

    @Override
    public List<Long> listReadableIds(boolean all, List<Long> deptIds, KbDomain domain) {
        QueryWrapper<KbDocumentEntity> wrapper = new QueryWrapper<KbDocumentEntity>()
                .select("id")
                .eq("deleted", 0)
                .eq("sync_status", KbSyncStatus.READY.name());
        if (domain != null) {
            wrapper.eq("domain", domain.name());
        }
        if (!all) {
            if (deptIds == null || deptIds.isEmpty()) {
                wrapper.eq("visibility", "ALL");
            } else {
                wrapper.and(w -> w.eq("visibility", "ALL").or().in("dept_id", deptIds));
            }
        }
        return mapper.selectList(wrapper).stream().map(e -> e.id).toList();
    }

    @Override
    public void insert(KbDocument document) {
        KbDocumentEntity entity = toEntity(document);
        entity.id = null;
        if (entity.createdAt == null) {
            entity.createdAt = LocalDateTime.now();
        }
        if (entity.deleted == null) {
            entity.deleted = false;
        }
        if (entity.chunkCnt == null) {
            entity.chunkCnt = 0;
        }
        if (entity.syncStatus == null) {
            entity.syncStatus = KbSyncStatus.PENDING;
        }
        mapper.insert(entity);
        document.id = entity.id;
        document.createdAt = entity.createdAt;
    }

    @Override
    public void updateSync(Long id, KbSyncStatus status, String syncMsg, Integer chunkCnt,
                           String externalDocId, LocalDateTime syncedAt) {
        UpdateWrapper<KbDocumentEntity> wrapper = new UpdateWrapper<KbDocumentEntity>()
                .eq("id", id)
                .set("sync_status", status == null ? KbSyncStatus.PENDING.name() : status.name())
                .set("sync_msg", syncMsg)
                .set("synced_at", syncedAt);
        if (chunkCnt != null) {
            wrapper.set("chunk_cnt", chunkCnt);
        }
        if (externalDocId != null) {
            wrapper.set("external_doc_id", externalDocId);
        }
        mapper.update(null, wrapper);
    }

    public static KbDocument toModel(KbDocumentEntity e) {
        KbDocument d = new KbDocument();
        d.id = e.id;
        d.title = e.title;
        d.domain = e.domain;
        d.deptId = e.deptId;
        d.deptCode = e.deptCode;
        d.visibility = e.visibility;
        d.fileKey = e.fileKey;
        d.fileHash = e.fileHash;
        d.sourceType = e.sourceType;
        d.sourceRef = e.sourceRef;
        d.datasetId = e.datasetId;
        d.externalDocId = e.externalDocId;
        d.parserId = e.parserId;
        d.embeddingModel = e.embeddingModel;
        d.chunkCnt = e.chunkCnt;
        d.syncStatus = e.syncStatus;
        d.syncMsg = e.syncMsg;
        d.syncedAt = e.syncedAt;
        d.createdBy = e.createdBy;
        d.deleted = e.deleted;
        d.createdAt = e.createdAt;
        return d;
    }

    private KbDocumentEntity toEntity(KbDocument d) {
        KbDocumentEntity e = new KbDocumentEntity();
        e.id = d.id;
        e.title = d.title;
        e.domain = d.domain;
        e.deptId = d.deptId;
        e.deptCode = d.deptCode;
        e.visibility = d.visibility;
        e.fileKey = d.fileKey;
        e.fileHash = d.fileHash;
        e.sourceType = d.sourceType;
        e.sourceRef = d.sourceRef;
        e.datasetId = d.datasetId;
        e.externalDocId = d.externalDocId;
        e.parserId = d.parserId;
        e.embeddingModel = d.embeddingModel;
        e.chunkCnt = d.chunkCnt;
        e.syncStatus = d.syncStatus;
        e.syncMsg = d.syncMsg;
        e.syncedAt = d.syncedAt;
        e.createdBy = d.createdBy;
        e.deleted = d.deleted;
        e.createdAt = d.createdAt;
        return e;
    }
}
