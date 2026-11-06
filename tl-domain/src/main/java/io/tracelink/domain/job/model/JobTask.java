package io.tracelink.domain.job.model;

import java.time.LocalDateTime;

/**
 * 一次异步任务。请求先落一条记录再执行，页面拿记录 ID 轮询，关掉浏览器不会丢进度。
 */
public class JobTask {

    public Long id;
    public JobType jobType;
    public String payload;
    public JobStatus status;
    public Integer progress;
    public Integer attempts;
    public Integer maxAttempts;
    public LocalDateTime nextRetryAt;
    public String lastError;
    public String lockedBy;
    public String resultText;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
