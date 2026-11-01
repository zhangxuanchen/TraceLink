package io.tracelink.infra.importing.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.importing.model.ImportMapping;
import io.tracelink.domain.importing.repository.ImportMappingRepository;
import io.tracelink.infra.importing.entity.ImportMappingEntity;
import io.tracelink.infra.importing.mapper.ImportMappingMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class ImportMappingRepositoryImpl implements ImportMappingRepository {

    private final ImportMappingMapper mapper;

    public ImportMappingRepositoryImpl(ImportMappingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ImportMapping findBySignature(String signature) {
        ImportMappingEntity entity = mapper.selectOne(new QueryWrapper<ImportMappingEntity>()
                .eq("template_signature", signature)
                .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        ImportMapping m = new ImportMapping();
        m.id = entity.id;
        m.templateSignature = entity.templateSignature;
        m.mapping = entity.mapping;
        m.hitCount = entity.hitCount;
        m.updatedAt = entity.updatedAt;
        return m;
    }

    @Override
    public void upsert(String signature, String mappingJson) {
        ImportMappingEntity existing = mapper.selectOne(new QueryWrapper<ImportMappingEntity>()
                .eq("template_signature", signature)
                .last("LIMIT 1"));
        if (existing == null) {
            ImportMappingEntity entity = new ImportMappingEntity();
            entity.templateSignature = signature;
            entity.mapping = mappingJson;
            entity.hitCount = 1;
            entity.updatedAt = LocalDateTime.now();
            mapper.insert(entity);
        } else {
            existing.mapping = mappingJson;
            existing.hitCount = (existing.hitCount == null ? 0 : existing.hitCount) + 1;
            existing.updatedAt = LocalDateTime.now();
            mapper.updateById(existing);
        }
    }
}
