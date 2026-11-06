-- 异步任务统一调度表（DESIGN 6.2 job_task）与设置页所需字段
-- 在 DESIGN 的列之外补三列：progress 供页面显示进度、result_text 记结果摘要、created_by 用于按人取数。
-- 同步接口保持不变，异步入口（导出 async=true）落一张 job_task 再执行，关掉页面也能回来查结果。

CREATE TABLE job_task (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_type      VARCHAR(32)  NOT NULL COMMENT 'PARSE_IMPORT EXPORT EVAL GATE_SNAPSHOT SYNC_KB',
  payload       JSON         NULL,
  status        VARCHAR(16)  NOT NULL COMMENT 'PENDING RUNNING DONE FAILED',
  progress      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-100 页面进度条',
  attempts      INT          NOT NULL DEFAULT 0,
  max_attempts  INT          NOT NULL DEFAULT 3,
  next_retry_at DATETIME     NULL,
  last_error    VARCHAR(500) NULL,
  locked_by     VARCHAR(64)  NULL COMMENT '实例与线程标识 抢占用',
  result_text   VARCHAR(500) NULL COMMENT '结果摘要 如 exportId=12 created=57',
  created_by    BIGINT       NULL COMMENT '发起人 非管理者只看自己的任务',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_job_status (status, next_retry_at),
  KEY idx_job_type (job_type, created_at),
  KEY idx_job_creator (created_by, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步任务';

-- 设置页需要看到参数最后修改人与修改时间；sys_config 已有 updated_by/updated_at 列，这里补一条通用说明
INSERT INTO sys_config (cfg_key, cfg_value, value_type, group_code, remark) VALUES
  ('job.retention.days', '30', 'INT', 'JOB', '异步任务记录保留天数，按操作频次增长，过期由快照任务清理');
