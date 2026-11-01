package io.tracelink.infra.importing.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.importing.model.ImportBatch;
import io.tracelink.domain.importing.model.ImportStatus;
import io.tracelink.domain.importing.repository.ImportBatchRepository;
import io.tracelink.infra.importing.entity.ImportBatchEntity;
import io.tracelink.infra.importing.mapper.ImportBatchMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ImportBatchRepositoryImpl implements ImportBatchRepository {

    private final ImportBatchMapper mapper;

    public ImportBatchRepositoryImpl(ImportBatchMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ImportBatch findByHash(String fileHash) {
        ImportBatchEntity entity = mapper.selectOne(new QueryWrapper<ImportBatchEntity>()
                .eq("file_hash", fileHash)
                .last("LIMIT 1"));
        return entity == null ? null : toModel(entity);
    }

    @Override
    public ImportBatch findById(Long id) {
        ImportBatchEntity entity = mapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public void insert(ImportBatch batch) {
        ImportBatchEntity entity = toEntity(batch);
        entity.id = null;
        if (entity.createdAt == null) {
            entity.createdAt = LocalDateTime.now();
        }
        mapper.insert(entity);
        batch.id = entity.id;
        batch.createdAt = entity.createdAt;
    }

    @Override
    public void updateStatus(Long id, String status, String errorMessage) {
        mapper.update(null, new UpdateWrapper<ImportBatchEntity>()
                .eq("id", id)
                .set("status", status)
                .set("error_msg", errorMessage));
    }

    @Override
    public void updateAnalysis(ImportBatch batch) {
        mapper.update(null, new UpdateWrapper<ImportBatchEntity>()
                .eq("id", batch.id)
                .set("status", batch.status == null ? null : batch.status.name())
                .set("sheet_total", batch.sheetTotal)
                .set("row_total", batch.rowTotal)
                .set("row_ok", batch.rowOk)
                .set("row_warn", batch.rowWarn)
                .set("row_error", batch.rowError)
                .set("mapping", batch.mapping)
                .set("error_msg", batch.errorMsg));
    }

    @Override
    public void updateMapping(Long id, String mapping) {
        mapper.update(null, new UpdateWrapper<ImportBatchEntity>()
                .eq("id", id)
                .set("mapping", mapping));
    }

    @Override
    public List<ImportBatch> listRecent(int limit) {
        return mapper.selectList(new QueryWrapper<ImportBatchEntity>()
                        .orderByDesc("id")
                        .last("LIMIT " + limit))
                .stream().map(ImportBatchRepositoryImpl::toModel).toList();
    }

    public static ImportBatch toModel(ImportBatchEntity e) {
        ImportBatch b = new ImportBatch();
        b.id = e.id;
        b.fileHash = e.fileHash;
        b.fileName = e.fileName;
        b.fileKey = e.fileKey;
        b.mode = e.mode;
        b.status = e.status;
        b.sheetTotal = e.sheetTotal;
        b.rowTotal = e.rowTotal;
        b.rowOk = e.rowOk;
        b.rowWarn = e.rowWarn;
        b.rowError = e.rowError;
        b.mapping = e.mapping;
        b.errorMsg = e.errorMsg;
        b.createdBy = e.createdBy;
        b.createdAt = e.createdAt;
        return b;
    }

    private static ImportBatchEntity toEntity(ImportBatch b) {
        ImportBatchEntity e = new ImportBatchEntity();
        e.id = b.id;
        e.fileHash = b.fileHash;
        e.fileName = b.fileName;
        e.fileKey = b.fileKey;
        e.mode = b.mode;
        e.status = b.status;
        e.sheetTotal = b.sheetTotal;
        e.rowTotal = b.rowTotal;
        e.rowOk = b.rowOk;
        e.rowWarn = b.rowWarn;
        e.rowError = b.rowError;
        e.mapping = b.mapping;
        e.errorMsg = b.errorMsg;
        e.createdBy = b.createdBy;
        e.createdAt = b.createdAt;
        return e;
    }
}
