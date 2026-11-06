package io.tracelink.domain.job.repository;

import io.tracelink.common.api.PageResult;
import io.tracelink.domain.job.model.JobStatus;
import io.tracelink.domain.job.model.JobTask;
import io.tracelink.domain.job.model.JobType;

/** 异步任务读写。重试与占用列留给调度器使用，本轮先由应用内线程池执行 */
public interface JobTaskRepository {

    void insert(JobTask job);

    void update(JobTask job);

    JobTask findById(Long id);

    /** createdBy 为 null 时按全量查，非管理者传本人 ID */
    PageResult<JobTask> page(Long createdBy, JobType jobType, JobStatus status, int pageNo, int pageSize);

    int deleteBefore(java.time.LocalDateTime time);
}
