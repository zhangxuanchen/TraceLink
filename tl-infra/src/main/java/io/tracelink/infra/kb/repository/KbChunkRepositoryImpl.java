package io.tracelink.infra.kb.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.kb.model.KbChunk;
import io.tracelink.domain.kb.repository.KbChunkRepository;
import io.tracelink.infra.kb.entity.KbChunkEntity;
import io.tracelink.infra.kb.mapper.KbChunkMapper;
import io.tracelink.infra.kb.mapper.KbChunkScoreRow;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Repository
public class KbChunkRepositoryImpl implements KbChunkRepository {

    private final KbChunkMapper mapper;

    public KbChunkRepositoryImpl(KbChunkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertBatch(List<KbChunk> chunks) {
        for (KbChunk chunk : chunks) {
            KbChunkEntity entity = toEntity(chunk);
            entity.id = null;
            if (entity.superseded == null) {
                entity.superseded = false;
            }
            if (entity.createdAt == null) {
                entity.createdAt = LocalDateTime.now();
            }
            mapper.insert(entity);
            chunk.id = entity.id;
        }
    }

    @Override
    public int supersedeByDoc(Long docId) {
        return mapper.update(null, new UpdateWrapper<KbChunkEntity>()
                .eq("doc_id", docId)
                .eq("superseded", 0)
                .set("superseded", 1));
    }

    @Override
    public int supersedeByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return mapper.update(null, new UpdateWrapper<KbChunkEntity>()
                .in("id", ids)
                .eq("superseded", 0)
                .set("superseded", 1));
    }

    @Override
    public List<KbChunk> listByDoc(Long docId, boolean includeSuperseded) {
        QueryWrapper<KbChunkEntity> wrapper = new QueryWrapper<KbChunkEntity>()
                .eq("doc_id", docId)
                .orderByAsc("seq");
        if (!includeSuperseded) {
            wrapper.eq("superseded", 0);
        }
        return mapper.selectList(wrapper).stream().map(KbChunkRepositoryImpl::toModel).toList();
    }

    @Override
    public KbChunk findById(Long id) {
        KbChunkEntity entity = mapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public KbChunk findByExternalId(String externalChunkId) {
        List<KbChunkEntity> rows = mapper.selectList(new QueryWrapper<KbChunkEntity>()
                .eq("external_chunk_id", externalChunkId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : toModel(rows.get(0));
    }

    @Override
    public long countValidByDoc(Long docId) {
        return mapper.selectCount(new QueryWrapper<KbChunkEntity>()
                .eq("doc_id", docId)
                .eq("superseded", 0));
    }

    @Override
    public List<ChunkHit> searchFulltext(List<Long> docIds, String question, int limit) {
        if (docIds == null || docIds.isEmpty() || question == null || question.isBlank()) {
            return List.of();
        }
        return mapper.searchFulltext(docIds, question.trim(), Math.max(1, limit)).stream()
                .map(KbChunkRepositoryImpl::toHit)
                .toList();
    }

    @Override
    public List<ChunkHit> searchLike(List<Long> docIds, String question, int limit) {
        if (docIds == null || docIds.isEmpty() || question == null || question.isBlank()) {
            return List.of();
        }
        List<String> terms = terms(question);
        if (terms.isEmpty()) {
            return List.of();
        }
        return mapper.searchLike(docIds, terms, Math.max(1, limit)).stream()
                .map(KbChunkRepositoryImpl::toHit)
                .toList();
    }

    /** 把问题拆成最多四个长度不小于二的片段，中文按标点切分，兜底用整句 */
    private List<String> terms(String question) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String trimmed = question.trim();
        for (String part : trimmed.split("[^\\p{IsHan}a-zA-Z0-9]+")) {
            if (part.length() >= 2 && terms.size() < 4) {
                terms.add(part);
            }
        }
        if (terms.isEmpty() && trimmed.length() >= 2) {
            terms.add(trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed);
        }
        return new ArrayList<>(terms);
    }

    private static ChunkHit toHit(KbChunkScoreRow row) {
        KbChunk chunk = new KbChunk();
        chunk.id = row.id;
        chunk.docId = row.docId;
        chunk.seq = row.seq;
        chunk.externalChunkId = row.externalChunkId;
        chunk.content = row.content;
        chunk.pageNo = row.pageNo;
        chunk.tokenCnt = row.tokenCnt;
        chunk.meta = row.meta;
        chunk.superseded = false;
        return new ChunkHit(chunk, row.score == null ? 0.0 : row.score);
    }

    public static KbChunk toModel(KbChunkEntity e) {
        KbChunk c = new KbChunk();
        c.id = e.id;
        c.docId = e.docId;
        c.seq = e.seq;
        c.externalChunkId = e.externalChunkId;
        c.content = e.content;
        c.pageNo = e.pageNo;
        c.tokenCnt = e.tokenCnt;
        c.meta = e.meta;
        c.superseded = e.superseded;
        c.createdAt = e.createdAt;
        return c;
    }

    private KbChunkEntity toEntity(KbChunk c) {
        KbChunkEntity e = new KbChunkEntity();
        e.id = c.id;
        e.docId = c.docId;
        e.seq = c.seq;
        e.externalChunkId = c.externalChunkId;
        e.content = c.content;
        e.pageNo = c.pageNo;
        e.tokenCnt = c.tokenCnt;
        e.meta = c.meta;
        e.superseded = c.superseded;
        e.createdAt = c.createdAt;
        return e;
    }
}
