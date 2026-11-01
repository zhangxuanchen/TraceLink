-- M5 会话、评价与建议：两张会话表、评价表、建议表，以及对应权限点
-- 与 docs/DESIGN.md 4.8、4.9、6.2 对齐。四张表只读业务数据，写回必须经人工确认（采纳走应用服务）。

CREATE TABLE chat_session (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id BIGINT       NOT NULL COMMENT '归属人',
  title       VARCHAR(120) NOT NULL COMMENT '取首轮提问前四十字',
  scope_type  VARCHAR(16)  NOT NULL DEFAULT 'DEPT' COMMENT 'SELF DEPT DEPT_TREE ALL',
  scope_id    BIGINT       NULL COMMENT '范围对象 通常是部门',
  msg_count   INT          NOT NULL DEFAULT 0,
  last_msg_at DATETIME     NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN ARCHIVED',
  deleted     TINYINT      NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_cs_emp_time (employee_id, last_msg_at),
  KEY idx_cs_scope (scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话';

CREATE TABLE chat_message (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT      NOT NULL,
  role       VARCHAR(16) NOT NULL COMMENT 'USER ASSISTANT TOOL',
  content    TEXT        NULL,
  citations  JSON        NULL COMMENT '引用数组 切片 工单 指标',
  tool_calls JSON        NULL COMMENT '工具名 入参摘要 结果条数 耗时',
  model_name VARCHAR(64) NULL COMMENT '本轮使用的模型',
  prompt_ver VARCHAR(32) NULL COMMENT '提示词版本',
  token_in   INT         NULL,
  token_out  INT         NULL,
  latency_ms INT         NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_cm_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话消息';

CREATE TABLE evaluation (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_type VARCHAR(16)  NOT NULL COMMENT 'EMPLOYEE TASK',
  target_id   BIGINT       NOT NULL,
  period      VARCHAR(16)  NOT NULL COMMENT '如 2026-11 或 2026Q4',
  score       DECIMAL(5,2) NULL,
  scores      JSON         NULL COMMENT '分维度得分',
  evidence    JSON         NULL COMMENT '证据 工单与记录 ID',
  confidence  DECIMAL(4,2) NULL COMMENT '样本量决定的置信度',
  sample_cnt  INT          NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT PUBLISHED APPEALED',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_eval (target_type, target_id, period),
  KEY idx_eval_period (period, target_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价 人与任务共用一张表';

CREATE TABLE ai_suggestion (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  type        VARCHAR(16) NOT NULL COMMENT 'DISPATCH ACTION DUE_DATE ESCALATE POLICY_GAP',
  target_type VARCHAR(16) NOT NULL COMMENT 'TASK DEPT EMPLOYEE',
  target_id   BIGINT      NOT NULL,
  payload     JSON        NOT NULL COMMENT '建议正文与可执行动作',
  evidence    JSON        NULL COMMENT '依据 工单 记录 指标',
  status      VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING ACCEPTED IGNORED',
  model_name  VARCHAR(64) NULL,
  prompt_ver  VARCHAR(32) NULL,
  token_in    INT         NULL,
  token_out   INT         NULL,
  latency_ms  INT         NULL,
  accepted_by BIGINT      NULL COMMENT '采纳人',
  accepted_at DATETIME    NULL COMMENT '采纳时间',
  accept_result VARCHAR(64) NULL COMMENT '采纳后的对象 如 TASK:12',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sug_target (target_type, target_id, status),
  KEY idx_sug_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 建议与待确认';

INSERT INTO permission (code, name, module) VALUES
  ('chat:ask', '会话问答', 'CHAT'),
  ('eval:view', '评价查看', 'SET'),
  ('eval:run', '评价生成', 'SET'),
  ('ai:suggest', 'AI 建议', 'SET');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p
  ON p.code IN ('chat:ask', 'eval:view', 'eval:run', 'ai:suggest')
WHERE r.code = 'SYS_ADMIN';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p
  ON p.code IN ('chat:ask', 'eval:view', 'eval:run', 'ai:suggest')
WHERE r.code = 'MANAGER';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p
  ON p.code IN ('chat:ask', 'ai:suggest')
WHERE r.code = 'OWNER';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p
  ON p.code = 'chat:ask'
WHERE r.code = 'EXECUTOR';

INSERT INTO sys_config (cfg_key, cfg_value, value_type, group_code, remark) VALUES
  ('eval.weight.delivery',   '30', 'DECIMAL', 'EVAL', '人员评价 交付力权重'),
  ('eval.weight.evidence',   '20', 'DECIMAL', 'EVAL', '人员评价 结果留痕权重'),
  ('eval.weight.response',   '15', 'DECIMAL', 'EVAL', '人员评价 响应力权重'),
  ('eval.weight.solution',   '20', 'DECIMAL', 'EVAL', '人员评价 解决力权重'),
  ('eval.weight.cooperation','10', 'DECIMAL', 'EVAL', '人员评价 协办力权重'),
  ('eval.weight.compliance', '5',  'DECIMAL', 'EVAL', '人员评价 规范性权重'),
  ('eval.confidence.sample', '20', 'INT',     'EVAL', '置信度达到 1 所需样本量'),
  ('ai.suggestion.gap.times', '3', 'INT',     'EVAL', '同类问题三十天内出现几次提出制度缺口建议');
