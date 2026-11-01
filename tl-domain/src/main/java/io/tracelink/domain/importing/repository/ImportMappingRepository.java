package io.tracelink.domain.importing.repository;

import io.tracelink.domain.importing.model.ImportMapping;

public interface ImportMappingRepository {

    ImportMapping findBySignature(String signature);

    void upsert(String signature, String mappingJson);
}
