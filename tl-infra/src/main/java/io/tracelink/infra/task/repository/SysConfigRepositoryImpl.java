package io.tracelink.infra.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.task.repository.SysConfigRepository;
import io.tracelink.infra.task.entity.SysConfigEntity;
import io.tracelink.infra.task.mapper.SysConfigMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SysConfigRepositoryImpl implements SysConfigRepository {

    private final SysConfigMapper configMapper;

    public SysConfigRepositoryImpl(SysConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @Override
    public String get(String key, String defaultValue) {
        SysConfigEntity entity = configMapper.selectOne(new QueryWrapper<SysConfigEntity>()
                .eq("cfg_key", key)
                .last("LIMIT 1"));
        return entity == null || entity.cfgValue == null || entity.cfgValue.isBlank()
                ? defaultValue : entity.cfgValue.trim();
    }

    @Override
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
