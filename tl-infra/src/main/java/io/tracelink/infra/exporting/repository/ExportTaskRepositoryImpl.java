package io.tracelink.infra.exporting.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.tracelink.domain.exporting.model.ExportTask;
import io.tracelink.domain.exporting.repository.ExportTaskRepository;
import io.tracelink.infra.exporting.entity.ExportTaskEntity;
import io.tracelink.infra.exporting.mapper.ExportTaskMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ExportTaskRepositoryImpl implements ExportTaskRepository {

    private final ExportTaskMapper mapper;

    public ExportTaskRepositoryImpl(ExportTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ExportTask task) {
        ExportTaskEntity entity = toEntity(task);
        entity.id = null;
        if (entity.createdAt == null) {
            entity.createdAt = LocalDateTime.now();
        }
        mapper.insert(entity);
        task.id = entity.id;
        task.createdAt = entity.createdAt;
    }

    @Override
    public void update(ExportTask task) {
        mapper.update(null, new UpdateWrapper<ExportTaskEntity>()
                .eq("id", task.id)
                .set("status", task.status == null ? null : task.status.name())
                .set("file_key", task.fileKey)
                .set("row_cnt", task.rowCnt)
                .set("error_msg", task.errorMsg)
                .set("params", task.params));
    }

    @Override
    public ExportTask findById(Long id) {
        ExportTaskEntity entity = mapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public List<ExportTask> listRecent(int limit) {
        return mapper.selectList(new QueryWrapper<ExportTaskEntity>()
                        .orderByDesc("id")
                        .last("LIMIT " + limit))
                .stream().map(ExportTaskRepositoryImpl::toModel).toList();
    }

    public static ExportTask toModel(ExportTaskEntity e) {
        ExportTask t = new ExportTask();
        t.id = e.id;
        t.templateCode = e.templateCode;
        t.params = e.params;
        t.status = e.status;
        t.fileKey = e.fileKey;
        t.rowCnt = e.rowCnt;
        t.errorMsg = e.errorMsg;
        t.createdBy = e.createdBy;
        t.createdAt = e.createdAt;
        return t;
    }

    private static ExportTaskEntity toEntity(ExportTask t) {
        ExportTaskEntity e = new ExportTaskEntity();
        e.id = t.id;
        e.templateCode = t.templateCode;
        e.params = t.params;
        e.status = t.status;
        e.fileKey = t.fileKey;
        e.rowCnt = t.rowCnt;
        e.errorMsg = t.errorMsg;
        e.createdBy = t.createdBy;
        e.createdAt = t.createdAt;
        return e;
    }
}
