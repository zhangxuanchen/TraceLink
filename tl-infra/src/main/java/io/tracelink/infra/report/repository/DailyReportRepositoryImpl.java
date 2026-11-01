package io.tracelink.infra.report.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.tracelink.domain.report.model.DailyReport;
import io.tracelink.domain.report.repository.DailyReportRepository;
import io.tracelink.infra.report.entity.DailyReportEntity;
import io.tracelink.infra.report.mapper.DailyReportMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class DailyReportRepositoryImpl implements DailyReportRepository {

    private final DailyReportMapper mapper;

    public DailyReportRepositoryImpl(DailyReportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DailyReport findByDateAndReporter(LocalDate reportDate, Long reporterId) {
        DailyReportEntity entity = mapper.selectOne(new QueryWrapper<DailyReportEntity>()
                .eq("report_date", reportDate)
                .eq("reporter_id", reporterId)
                .last("LIMIT 1"));
        return entity == null ? null : toModel(entity);
    }

    @Override
    public List<DailyReport> listBetween(LocalDate from, LocalDate to, Long reporterId) {
        QueryWrapper<DailyReportEntity> wrapper = new QueryWrapper<DailyReportEntity>()
                .ge("report_date", from)
                .le("report_date", to)
                .eq(reporterId != null, "reporter_id", reporterId)
                .orderByDesc("report_date");
        return mapper.selectList(wrapper).stream().map(DailyReportRepositoryImpl::toModel).toList();
    }

    @Override
    public List<DailyReport> listByBatch(Long batchId) {
        return mapper.selectList(new QueryWrapper<DailyReportEntity>()
                        .eq("import_batch_id", batchId))
                .stream().map(DailyReportRepositoryImpl::toModel).toList();
    }

    @Override
    public void upsert(DailyReport report) {
        DailyReportEntity existing = mapper.selectOne(new QueryWrapper<DailyReportEntity>()
                .eq("report_date", report.reportDate)
                .eq("reporter_id", report.reporterId)
                .last("LIMIT 1"));
        if (existing == null) {
            DailyReportEntity entity = new DailyReportEntity();
            entity.reportDate = report.reportDate;
            entity.reporterId = report.reporterId;
            entity.sourceSheet = report.sourceSheet;
            entity.importBatchId = report.importBatchId;
            entity.raw = report.raw;
            entity.createdAt = LocalDateTime.now();
            mapper.insert(entity);
            report.id = entity.id;
            report.createdAt = entity.createdAt;
        } else {
            existing.sourceSheet = report.sourceSheet;
            existing.importBatchId = report.importBatchId;
            existing.raw = report.raw;
            mapper.updateById(existing);
            report.id = existing.id;
            report.createdAt = existing.createdAt;
        }
    }

    public static DailyReport toModel(DailyReportEntity e) {
        DailyReport r = new DailyReport();
        r.id = e.id;
        r.reportDate = e.reportDate;
        r.reporterId = e.reporterId;
        r.sourceSheet = e.sourceSheet;
        r.importBatchId = e.importBatchId;
        r.raw = e.raw;
        r.createdAt = e.createdAt;
        return r;
    }
}
