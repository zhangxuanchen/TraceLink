package io.tracelink.infra.job.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.tracelink.common.api.PageResult;
import io.tracelink.domain.job.model.JobStatus;
import io.tracelink.domain.job.model.JobTask;
import io.tracelink.domain.job.model.JobType;
import io.tracelink.domain.job.repository.JobTaskRepository;
import io.tracelink.infra.job.entity.JobTaskEntity;
import io.tracelink.infra.job.mapper.JobTaskMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JobTaskRepositoryImpl implements JobTaskRepository {

    private final JobTaskMapper mapper;

    public JobTaskRepositoryImpl(JobTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(JobTask job) {
        JobTaskEntity entity = toEntity(job);
        mapper.insert(entity);
        job.id = entity.id;
    }

    @Override
    public void update(JobTask job) {
        mapper.updateById(toEntity(job));
    }

    @Override
    public JobTask findById(Long id) {
        JobTaskEntity entity = mapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public PageResult<JobTask> page(Long createdBy, JobType jobType, JobStatus status, int pageNo, int pageSize) {
        QueryWrapper<JobTaskEntity> wrapper = new QueryWrapper<JobTaskEntity>()
                .eq(createdBy != null, "created_by", createdBy)
                .eq(jobType != null, "job_type", jobType)
                .eq(status != null, "status", status)
                .orderByDesc("id");
        Page<JobTaskEntity> page = mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<JobTask> records = page.getRecords().stream().map(JobTaskRepositoryImpl::toModel).toList();
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public int deleteBefore(LocalDateTime time) {
        // 只清理已结束的任务，运行中的旧任务留给执行线程收尾
        return mapper.delete(new QueryWrapper<JobTaskEntity>()
                .lt("created_at", time)
                .in("status", "DONE", "FAILED"));
    }

    private static JobTaskEntity toEntity(JobTask job) {
        JobTaskEntity entity = new JobTaskEntity();
        entity.id = job.id;
        entity.jobType = job.jobType;
        entity.payload = job.payload;
        entity.status = job.status;
        entity.progress = job.progress;
        entity.attempts = job.attempts;
        entity.maxAttempts = job.maxAttempts;
        entity.nextRetryAt = job.nextRetryAt;
        entity.lastError = job.lastError;
        entity.lockedBy = job.lockedBy;
        entity.resultText = job.resultText;
        entity.createdBy = job.createdBy;
        entity.createdAt = job.createdAt;
        entity.updatedAt = job.updatedAt;
        return entity;
    }

    public static JobTask toModel(JobTaskEntity entity) {
        JobTask job = new JobTask();
        job.id = entity.id;
        job.jobType = entity.jobType;
        job.payload = entity.payload;
        job.status = entity.status;
        job.progress = entity.progress;
        job.attempts = entity.attempts;
        job.maxAttempts = entity.maxAttempts;
        job.nextRetryAt = entity.nextRetryAt;
        job.lastError = entity.lastError;
        job.lockedBy = entity.lockedBy;
        job.resultText = entity.resultText;
        job.createdBy = entity.createdBy;
        job.createdAt = entity.createdAt;
        job.updatedAt = entity.updatedAt;
        return job;
    }
}
