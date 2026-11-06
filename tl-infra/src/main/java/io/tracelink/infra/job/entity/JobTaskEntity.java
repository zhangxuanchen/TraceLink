package io.tracelink.infra.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.job.model.JobStatus;
import io.tracelink.domain.job.model.JobType;

import java.time.LocalDateTime;

@TableName("job_task")
public class JobTaskEntity {

    @TableId(type = IdType.AUTO)
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
