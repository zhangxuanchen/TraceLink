package io.tracelink.domain.importing.repository;

import io.tracelink.domain.importing.model.ImportBatch;

import java.util.List;

public interface ImportBatchRepository {

    ImportBatch findByHash(String fileHash);

    ImportBatch findById(Long id);

    void insert(ImportBatch batch);

    void updateStatus(Long id, String status, String errorMessage);

    void updateAnalysis(ImportBatch batch);

    void updateMapping(Long id, String mapping);

    List<ImportBatch> listRecent(int limit);
}
