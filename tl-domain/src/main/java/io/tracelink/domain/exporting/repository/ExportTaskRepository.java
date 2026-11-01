package io.tracelink.domain.exporting.repository;

import io.tracelink.domain.exporting.model.ExportTask;

import java.util.List;

public interface ExportTaskRepository {

    void insert(ExportTask task);

    void update(ExportTask task);

    ExportTask findById(Long id);

    List<ExportTask> listRecent(int limit);
}
