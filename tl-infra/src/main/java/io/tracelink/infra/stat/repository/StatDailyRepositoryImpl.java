package io.tracelink.infra.stat.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.stat.model.StatDaily;
import io.tracelink.domain.stat.model.StatDimType;
import io.tracelink.domain.stat.repository.StatDailyRepository;
import io.tracelink.infra.stat.entity.StatDailyEntity;
import io.tracelink.infra.stat.mapper.StatDailyMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class StatDailyRepositoryImpl implements StatDailyRepository {

    private final StatDailyMapper mapper;

    public StatDailyRepositoryImpl(StatDailyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void deleteByDate(LocalDate date) {
        mapper.delete(new QueryWrapper<StatDailyEntity>().eq("stat_date", date));
    }

    @Override
    public void upsertAll(List<StatDaily> rows) {
        for (StatDaily row : rows) {
            StatDailyEntity existing = mapper.selectOne(new QueryWrapper<StatDailyEntity>()
                    .eq("stat_date", row.statDate)
                    .eq("dim_type", row.dimType == null ? null : row.dimType.name())
                    .eq("dim_code", row.dimCode)
                    .eq("metric_code", row.metricCode)
                    .last("LIMIT 1"));
            if (existing == null) {
                StatDailyEntity entity = new StatDailyEntity();
                entity.statDate = row.statDate;
                entity.dimType = row.dimType;
                entity.dimCode = row.dimCode;
                entity.metricCode = row.metricCode;
                entity.value = row.value;
                entity.updatedAt = LocalDateTime.now();
                mapper.insert(entity);
            } else {
                existing.value = row.value;
                existing.updatedAt = LocalDateTime.now();
                mapper.updateById(existing);
            }
        }
    }

    @Override
    public List<StatDaily> findBetween(LocalDate from, LocalDate to) {
        return mapper.selectList(new QueryWrapper<StatDailyEntity>()
                        .ge("stat_date", from)
                        .le("stat_date", to)
                        .orderByAsc("stat_date"))
                .stream().map(StatDailyRepositoryImpl::toModel).toList();
    }

    @Override
    public List<StatDaily> find(LocalDate date, StatDimType dimType) {
        return mapper.selectList(new QueryWrapper<StatDailyEntity>()
                        .eq("stat_date", date)
                        .eq("dim_type", dimType.name()))
                .stream().map(StatDailyRepositoryImpl::toModel).toList();
    }

    public static StatDaily toModel(StatDailyEntity e) {
        StatDaily s = new StatDaily();
        s.id = e.id;
        s.statDate = e.statDate;
        s.dimType = e.dimType;
        s.dimCode = e.dimCode;
        s.metricCode = e.metricCode;
        s.value = e.value;
        s.updatedAt = e.updatedAt;
        return s;
    }
}
