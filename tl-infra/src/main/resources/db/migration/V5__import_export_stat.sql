-- M2 地基：导入批次与行、映射模板、日报页、导出任务、指标与统计预聚合
-- 字段口径见 docs/DESIGN.md 6.2，行为约束见 4.5 导入、4.6 导出、6.5 一致性

CREATE TABLE import_batch (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_hash   CHAR(64)     NOT NULL,
  file_name   VARCHAR(255) NOT NULL,
  file_key    VARCHAR(255) NOT NULL COMMENT '文件卷相对路径',
  mode        VARCHAR(32)  NOT NULL,
  status      VARCHAR(16)  NOT NULL COMMENT 'UPLOADED ANALYZING PREVIEWED CONFIRMED FAILED REVOKED',
  sheet_total INT          NULL,
  row_total   INT          NULL,
  row_ok      INT          NULL,
  row_warn    INT          NULL,
  row_error   INT          NULL,
  mapping     JSON         NULL,
  error_msg   VARCHAR(500) NULL COMMENT '解析失败原因',
  created_by  BIGINT       NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_batch_hash (file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入批次';

CREATE TABLE import_row (
  id       BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT      NOT NULL,
  sheet    VARCHAR(64) NOT NULL,
  row_no   INT         NOT NULL,
  raw      JSON        NOT NULL COMMENT '原始行快照',
  parsed   JSON        NULL,
  level    VARCHAR(8)  NOT NULL COMMENT 'OK WARN ERROR',
  issues   JSON        NULL,
  task_id  BIGINT      NULL,
  fingerprint VARCHAR(64) NULL COMMENT '行指纹 工作表+序号+标题 二次导入去重',
  KEY idx_row_batch (batch_id, level),
  KEY idx_row_fp (fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入行';

CREATE TABLE import_mapping (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_signature VARCHAR(128) NOT NULL COMMENT '列名与区块位置拼接的签名',
  mapping            JSON         NOT NULL COMMENT '列到字段的映射与置信度',
  hit_count          INT          NOT NULL DEFAULT 1,
  updated_at         DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mapping_sig (template_signature)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入映射模板复用';

CREATE TABLE daily_report (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_date     DATE        NOT NULL,
  reporter_id     BIGINT      NOT NULL,
  source_sheet    VARCHAR(64) NULL COMMENT '工作表名 如 20261025',
  import_batch_id BIGINT      NULL,
  raw             JSON        NULL COMMENT '页级原始快照 复核用',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_report_date_reporter (report_date, reporter_id),
  KEY idx_report_batch (import_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日报页 一次汇报一页';

CREATE TABLE export_task (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_code VARCHAR(32)  NOT NULL COMMENT 'DAILY WEEKLY LEDGER PERF CHART',
  params        JSON         NULL COMMENT '查询条件与范围',
  status        VARCHAR(16)  NOT NULL COMMENT 'PENDING RUNNING DONE FAILED',
  file_key      VARCHAR(255) NULL,
  row_cnt       INT          NULL,
  error_msg     VARCHAR(500) NULL,
  created_by    BIGINT       NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_export_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出任务';

CREATE TABLE task_metric (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id     BIGINT        NULL,
  stat_date   DATE          NOT NULL,
  dim_type    VARCHAR(16)   NOT NULL COMMENT 'PROCESS DEPT EMPLOYEE',
  dim_code    VARCHAR(32)   NOT NULL,
  metric_code VARCHAR(32)   NOT NULL COMMENT 'PLAN_QTY ACT_QTY RATE',
  value       DECIMAL(14,3) NOT NULL,
  unit        VARCHAR(16)   NULL,
  extra       JSON          NULL,
  UNIQUE KEY uk_metric (stat_date, dim_type, dim_code, metric_code),
  KEY idx_metric_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标';

CREATE TABLE stat_daily (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date   DATE          NOT NULL,
  dim_type    VARCHAR(16)   NOT NULL COMMENT 'DEPT EMPLOYEE TYPE PROCESS',
  dim_code    VARCHAR(32)   NOT NULL,
  metric_code VARCHAR(32)   NOT NULL,
  value       DECIMAL(14,3) NOT NULL,
  updated_at  DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_stat (stat_date, dim_type, dim_code, metric_code),
  KEY idx_metric_code (metric_code, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统计日预聚合';

-- 导入/导出的阈值参数，运营可在“参数配置”里调整
INSERT INTO sys_config (cfg_key, cfg_value, value_type, group_code, remark) VALUES
  ('import.mapping.hit', '1', 'INT', 'SIMILARITY', '映射模板命中次数累计，用于置信度排序'),
  ('export.retain.days', '30', 'INT', 'EXPORT', '导出文件在文件卷保留天数') ON DUPLICATE KEY UPDATE cfg_value = VALUES(cfg_value);
