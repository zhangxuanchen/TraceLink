-- M1 工单与追踪：工单主体、参与人、问题、措施、追踪记录、审计与参数配置
-- 与 docs/DESIGN.md 6.2 对齐。task_attachment、task_metric 与 gate_rule 分别留到 M2、M3

CREATE TABLE task (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_no             VARCHAR(32)  NOT NULL COMMENT 'T-20261101-001',
  title               VARCHAR(255) NOT NULL,
  description         TEXT         NULL,
  type                VARCHAR(32)  NOT NULL COMMENT 'PATROL QUALITY PLAN EQUIP CUSTOMER SAFETY OTHER',
  source_type         VARCHAR(16)  NOT NULL COMMENT 'MANUAL REPORT CHAT',
  source_ref          JSON         NULL COMMENT '日报定位或会话消息 ID 列表',
  dept_id             BIGINT       NOT NULL,
  owner_id            BIGINT       NOT NULL,
  primary_assignee_id BIGINT       NULL,
  assignee_text       VARCHAR(128) NULL COMMENT '源数据里的群体名或部门名',
  priority            TINYINT      NOT NULL DEFAULT 2,
  status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
  plan_start          DATETIME     NULL,
  plan_end            DATETIME     NOT NULL,
  actual_end          DATETIME     NULL,
  progress            TINYINT      NOT NULL DEFAULT 0,
  accept_criteria     JSON         NULL COMMENT '验收条件数组',
  result_summary      TEXT         NULL COMMENT '结果说明',
  delay_count         INT          NOT NULL DEFAULT 0,
  reject_count        INT          NOT NULL DEFAULT 0,
  urge_count          INT          NOT NULL DEFAULT 0,
  closure_level       VARCHAR(16)  NULL COMMENT 'STRONG WEAK NONE',
  recurrence_of       BIGINT       NULL COMMENT '复发来源工单',
  parent_id           BIGINT       NULL COMMENT '父工单',
  tags                VARCHAR(255) NULL,
  deleted             TINYINT      NOT NULL DEFAULT 0,
  created_by          BIGINT       NOT NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_task_no (task_no),
  KEY idx_task_status_end (status, plan_end),
  KEY idx_task_dept_status (dept_id, status),
  KEY idx_task_owner (owner_id),
  KEY idx_task_assignee (primary_assignee_id),
  KEY idx_task_recurrence (recurrence_of),
  FULLTEXT KEY ft_task_title (title) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单';

CREATE TABLE task_assignee (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id     BIGINT      NOT NULL,
  employee_id BIGINT      NOT NULL,
  role_type   VARCHAR(16) NOT NULL DEFAULT 'ASSIST' COMMENT 'ASSIST COOPERATE',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_assignee (task_id, employee_id),
  KEY idx_assignee_emp (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单参与人 只记参与不承担验收与逾期';

CREATE TABLE task_issue (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id   BIGINT       NOT NULL,
  seq       INT          NOT NULL,
  content   VARCHAR(500) NOT NULL,
  category  VARCHAR(32)  NULL,
  severity  TINYINT      NOT NULL DEFAULT 2,
  status    VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
  closed_at DATETIME     NULL,
  KEY idx_issue_task (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题项';

CREATE TABLE task_action (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id       BIGINT       NOT NULL,
  issue_id      BIGINT       NULL,
  seq           INT          NOT NULL,
  content       VARCHAR(500) NOT NULL,
  owner_id      BIGINT       NULL,
  due_date      DATE         NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
  task_child_id BIGINT       NULL COMMENT '升级为子工单',
  KEY idx_action_task (task_id),
  KEY idx_action_owner_due (owner_id, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='措施项';

CREATE TABLE task_checkin (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id           BIGINT       NOT NULL,
  employee_id       BIGINT       NOT NULL,
  check_date        DATE         NOT NULL,
  progress          TINYINT      NOT NULL,
  content           TEXT         NOT NULL,
  blocker_text      VARCHAR(500) NULL,
  next_step         VARCHAR(500) NULL,
  plan_end_snapshot DATETIME     NULL,
  result_type       VARCHAR(16)  NOT NULL DEFAULT 'PROGRESS',
  closure_level     VARCHAR(16)  NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ci_task_date (task_id, check_date),
  KEY idx_ci_emp_date (employee_id, check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追踪记录';

CREATE TABLE audit_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id   BIGINT      NOT NULL,
  action     VARCHAR(32) NOT NULL COMMENT 'CREATE UPDATE STATUS DELETE URGE',
  biz_type   VARCHAR(16) NOT NULL,
  biz_id     BIGINT      NOT NULL,
  `before`   JSON        NULL,
  `after`    JSON        NULL,
  trace_id   VARCHAR(64) NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_biz (biz_type, biz_id, created_at),
  KEY idx_audit_actor (actor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计 写操作全部留痕';

CREATE TABLE sys_config (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  cfg_key    VARCHAR(64)  NOT NULL COMMENT '如 similarity.threshold alert.gap.first',
  cfg_value  VARCHAR(255) NOT NULL,
  value_type VARCHAR(16)  NOT NULL COMMENT 'INT DECIMAL STRING BOOLEAN JSON',
  group_code VARCHAR(32)  NOT NULL COMMENT 'SIMILARITY GATE EVAL ALERT EXPORT',
  remark     VARCHAR(255) NULL COMMENT '含义与取值范围',
  updated_by BIGINT       NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cfg (cfg_key),
  KEY idx_cfg_group (group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参数配置 阈值与权重';

-- Q13 取 A：完成条件集合与权重在 M3 由 gate_rule 落地，这里只记录口径版本
-- Q14 取 A：缺口滞留三日提醒承担者、七日提醒负责人
INSERT INTO sys_config (cfg_key, cfg_value, value_type, group_code, remark) VALUES
  ('similarity.threshold',  '0.85', 'DECIMAL', 'SIMILARITY', '复发判定标题相似度阈值 0 到 1，初值 0.85，试点期按真实数据校准（R7）'),
  ('similarity.window.days', '180', 'INT',     'SIMILARITY', '复发判定回看已验收工单的天数（R7）'),
  ('alert.gap.first',       '3',    'INT',     'ALERT',      '缺口滞留提醒承担者的天数，Q14 默认三日'),
  ('alert.gap.second',      '7',    'INT',     'ALERT',      '缺口滞留提醒负责人的天数，Q14 默认七日'),
  ('task.reschedule.limit', '3',    'INT',     'ALERT',      '改期累计次数上限，超过后要求填根因与新措施（R6）'),
  ('task.urge.window.hours','24',   'INT',     'ALERT',      '同一工单催办最小间隔小时数');
