-- M3 完成地图：完成条件口径、日快照、缺口聚合与两级提醒
-- 与 docs/DESIGN.md 4.10 与 6.2 对齐。九条条件全部从既有表派生，不新增业务字段。

CREATE TABLE gate_rule (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_type   VARCHAR(32) NOT NULL COMMENT '工单类型 ALL 表示默认口径',
  gate_code   VARCHAR(16) NOT NULL COMMENT 'G1 到 G9',
  weight      INT         NOT NULL DEFAULT 0 COMMENT '完成度权重',
  required    TINYINT     NOT NULL DEFAULT 1 COMMENT '1 必须达成 0 仅提示',
  holder_role VARCHAR(16) NULL COMMENT '缺口承担角色 PRIMARY OWNER ACTION_OWNER',
  sort_no     INT         NOT NULL DEFAULT 0,
  enabled     TINYINT     NOT NULL DEFAULT 1,
  UNIQUE KEY uk_gate_rule (task_type, gate_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='完成条件口径';

CREATE TABLE task_gate_snapshot (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  snap_date  DATE        NOT NULL,
  task_id    BIGINT      NOT NULL,
  dept_id    BIGINT      NOT NULL COMMENT '冗余存部门 供矩阵按部门取整列',
  task_type  VARCHAR(32) NOT NULL,
  gate_code  VARCHAR(16) NOT NULL,
  state      VARCHAR(8)  NOT NULL COMMENT 'DONE TODO NA',
  gap_days   INT         NOT NULL DEFAULT 0 COMMENT '该缺口已持续天数',
  holder_id  BIGINT      NULL COMMENT '缺口承担者',
  UNIQUE KEY uk_snap (snap_date, task_id, gate_code),
  KEY idx_snap_dept (snap_date, dept_id, gate_code),
  KEY idx_snap_task (task_id, snap_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='完成条件日快照 保留一百八十天';

CREATE TABLE gate_gap_daily (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  snap_date    DATE        NOT NULL,
  dept_id      BIGINT      NOT NULL,
  gate_code    VARCHAR(16) NOT NULL,
  done_count   INT         NOT NULL DEFAULT 0,
  todo_count   INT         NOT NULL DEFAULT 0,
  na_count     INT         NOT NULL DEFAULT 0,
  max_gap_days INT         NOT NULL DEFAULT 0,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_gap_daily (snap_date, dept_id, gate_code),
  KEY idx_gap_daily_date (snap_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺口按日按部门按条件聚合 快照回收后仍可看趋势';

CREATE TABLE notification (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  recipient_id  BIGINT       NOT NULL COMMENT '收件人 承担者或负责人',
  type          VARCHAR(16)  NOT NULL DEFAULT 'GAP' COMMENT 'GAP',
  task_id       BIGINT       NOT NULL,
  task_no       VARCHAR(32)  NULL,
  gate_code     VARCHAR(16)  NOT NULL,
  level         VARCHAR(16)  NOT NULL COMMENT 'NEW FIRST SECOND',
  gap_days      INT          NOT NULL DEFAULT 0,
  episode_start DATE         NOT NULL COMMENT '同一缺口周期的起点 用于幂等',
  title         VARCHAR(200) NOT NULL,
  content       VARCHAR(500) NOT NULL,
  read_flag     TINYINT      NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_notify_episode (task_id, gate_code, level, episode_start),
  KEY idx_notify_recipient (recipient_id, read_flag, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺口提醒收件箱 只推新增与超阈值 不每日重发';

INSERT INTO sys_config (cfg_key, cfg_value, value_type, group_code, remark) VALUES
  ('gate.snapshot.retention.days', '180', 'INT', 'GATE', '完成条件日快照保留天数，更早只留按日按部门按条件的聚合计数');

-- 默认口径：九条条件，权重合计一百
INSERT INTO gate_rule (task_type, gate_code, weight, required, holder_role, sort_no, enabled) VALUES
  ('ALL', 'G1', 10, 1, 'PRIMARY',      1, 1),
  ('ALL', 'G2',  5, 1, 'OWNER',        2, 1),
  ('ALL', 'G3', 15, 1, 'PRIMARY',      3, 1),
  ('ALL', 'G4', 15, 1, 'ACTION_OWNER', 4, 1),
  ('ALL', 'G5', 15, 1, 'PRIMARY',      5, 1),
  ('ALL', 'G6', 10, 1, 'PRIMARY',      6, 1),
  ('ALL', 'G7', 10, 1, 'OWNER',        7, 1),
  ('ALL', 'G8', 10, 0, 'PRIMARY',      8, 1),
  ('ALL', 'G9', 10, 1, 'OWNER',        9, 1);

-- 现场巡查类：不看 G8 指标，证据权重抬高到 20，合计仍为一百
INSERT INTO gate_rule (task_type, gate_code, weight, required, holder_role, sort_no, enabled) VALUES
  ('PATROL', 'G6', 20, 1, 'PRIMARY', 6, 1),
  ('PATROL', 'G8',  0, 0, 'PRIMARY', 8, 0);

-- 计划与制度类：不看 G6 证据，措施与结果权重各加五，合计仍为一百
INSERT INTO gate_rule (task_type, gate_code, weight, required, holder_role, sort_no, enabled) VALUES
  ('PLAN', 'G4', 20, 1, 'ACTION_OWNER', 4, 1),
  ('PLAN', 'G5', 20, 1, 'PRIMARY',      5, 1),
  ('PLAN', 'G6',  0, 0, 'PRIMARY',      6, 0);

-- 客户与外部依赖类：G7 阻碍权重抬高到 20，接单与证据各降五，合计仍为一百
INSERT INTO gate_rule (task_type, gate_code, weight, required, holder_role, sort_no, enabled) VALUES
  ('CUSTOMER', 'G1',  5, 1, 'PRIMARY', 1, 1),
  ('CUSTOMER', 'G6',  5, 1, 'PRIMARY', 6, 1),
  ('CUSTOMER', 'G7', 20, 1, 'OWNER',   7, 1);
