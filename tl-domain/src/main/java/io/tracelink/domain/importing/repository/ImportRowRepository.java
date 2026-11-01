package io.tracelink.domain.importing.repository;

import io.tracelink.domain.importing.model.ImportRow;
import io.tracelink.domain.importing.model.RowLevel;

import java.util.List;

public interface ImportRowRepository {

    void insertAll(List<ImportRow> rows);

    List<ImportRow> listByBatch(Long batchId, RowLevel level);

    List<ImportRow> listBuildable(Long batchId);

    void update(ImportRow row);

    void deleteByBatch(Long batchId);

    /** 跨批次指纹查询：命中已确认批次的同指纹行，说明这份内容已入过库 */
    ImportRow findConfirmedByFingerprint(String fingerprint, Long excludeBatchId);
}
