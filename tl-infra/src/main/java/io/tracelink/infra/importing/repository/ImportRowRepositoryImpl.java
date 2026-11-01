package io.tracelink.infra.importing.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.importing.model.ImportRow;
import io.tracelink.domain.importing.model.RowLevel;
import io.tracelink.domain.importing.repository.ImportRowRepository;
import io.tracelink.infra.importing.entity.ImportRowEntity;
import io.tracelink.infra.importing.mapper.ImportRowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ImportRowRepositoryImpl implements ImportRowRepository {

    private final ImportRowMapper mapper;

    public ImportRowRepositoryImpl(ImportRowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertAll(List<ImportRow> rows) {
        for (ImportRow row : rows) {
            ImportRowEntity entity = toEntity(row);
            entity.id = null;
            mapper.insert(entity);
            row.id = entity.id;
        }
    }

    @Override
    public List<ImportRow> listByBatch(Long batchId, RowLevel level) {
        QueryWrapper<ImportRowEntity> wrapper = new QueryWrapper<ImportRowEntity>()
                .eq("batch_id", batchId)
                .eq(level != null, "level", level == null ? null : level.name())
                .orderByAsc("sheet")
                .orderByAsc("row_no");
        return mapper.selectList(wrapper).stream().map(ImportRowRepositoryImpl::toModel).toList();
    }

    @Override
    public List<ImportRow> listBuildable(Long batchId) {
        return mapper.selectList(new QueryWrapper<ImportRowEntity>()
                        .eq("batch_id", batchId)
                        .ne("level", RowLevel.ERROR.name())
                        .orderByAsc("sheet")
                        .orderByAsc("row_no"))
                .stream().map(ImportRowRepositoryImpl::toModel).toList();
    }

    @Override
    public void update(ImportRow row) {
        mapper.update(null, new UpdateWrapper<ImportRowEntity>()
                .eq("id", row.id)
                .set("parsed", row.parsed)
                .set("level", row.level == null ? null : row.level.name())
                .set("issues", row.issues)
                .set("task_id", row.taskId)
                .set("fingerprint", row.fingerprint));
    }

    @Override
    public void deleteByBatch(Long batchId) {
        mapper.delete(new QueryWrapper<ImportRowEntity>().eq("batch_id", batchId));
    }

    @Override
    public ImportRow findConfirmedByFingerprint(String fingerprint, Long excludeBatchId) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        List<ImportRowEntity> rows = mapper.selectList(new QueryWrapper<ImportRowEntity>()
                .eq("fingerprint", fingerprint)
                .isNotNull("task_id")
                .ne(excludeBatchId != null, "batch_id", excludeBatchId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : toModel(rows.get(0));
    }

    public static ImportRow toModel(ImportRowEntity e) {
        ImportRow r = new ImportRow();
        r.id = e.id;
        r.batchId = e.batchId;
        r.sheet = e.sheet;
        r.rowNo = e.rowNo == null ? 0 : e.rowNo;
        r.raw = e.raw;
        r.parsed = e.parsed;
        r.level = e.level;
        r.issues = e.issues;
        r.taskId = e.taskId;
        r.fingerprint = e.fingerprint;
        return r;
    }

    private static ImportRowEntity toEntity(ImportRow r) {
        ImportRowEntity e = new ImportRowEntity();
        e.id = r.id;
        e.batchId = r.batchId;
        e.sheet = r.sheet;
        e.rowNo = r.rowNo;
        e.raw = r.raw;
        e.parsed = r.parsed;
        e.level = r.level;
        e.issues = r.issues;
        e.taskId = r.taskId;
        e.fingerprint = r.fingerprint;
        return e;
    }
}
