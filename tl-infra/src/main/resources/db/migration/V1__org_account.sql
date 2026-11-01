-- M0 组织与账号：部门、职位、人员、别名、登录账号、角色、权限
-- 与 docs/DESIGN.md 6.2 保持一致，新增 user_role 表补齐“账号到角色”的缺环

CREATE TABLE dept (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id          BIGINT NOT NULL DEFAULT 0,
  code               VARCHAR(32)  NOT NULL COMMENT '部门编码',
  name               VARCHAR(64)  NOT NULL COMMENT '部门名称',
  path               VARCHAR(255) NOT NULL COMMENT '物化路径 /1/5/12/',
  level              TINYINT      NOT NULL DEFAULT 1,
  leader_employee_id BIGINT       NULL COMMENT '部门负责人',
  headcount          INT          NULL COMMENT '编制人数',
  sort_no            INT          NOT NULL DEFAULT 0,
  status             TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dept_code (code),
  KEY idx_dept_path (path),
  KEY idx_dept_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

CREATE TABLE position (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  dept_id    BIGINT      NOT NULL,
  code       VARCHAR(32) NOT NULL COMMENT '职位编码',
  name       VARCHAR(64) NOT NULL,
  job_level  TINYINT     NOT NULL DEFAULT 1 COMMENT '职级 1 到 9',
  is_manager TINYINT     NOT NULL DEFAULT 0 COMMENT '1 管理岗 可被选为负责人',
  sort_no    INT         NOT NULL DEFAULT 0,
  status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_position_dept_code (dept_id, code),
  KEY idx_position_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位';

CREATE TABLE employee (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  emp_no      VARCHAR(32) NOT NULL COMMENT '工号',
  name        VARCHAR(32) NOT NULL,
  dept_id     BIGINT      NOT NULL,
  position_id BIGINT      NULL,
  manager_id  BIGINT      NULL COMMENT '直属上级 用于逾期升级链',
  phone       VARCHAR(32) NULL,
  email       VARCHAR(64) NULL,
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1 在职 2 离职 3 试用',
  join_date   DATE        NULL,
  leave_date  DATE        NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_emp_no (emp_no),
  KEY idx_emp_name (name),
  KEY idx_emp_dept (dept_id),
  KEY idx_emp_manager (manager_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员';

CREATE TABLE employee_alias (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id BIGINT      NOT NULL,
  alias       VARCHAR(32) NOT NULL COMMENT '别名 如 张总 老沈',
  source      VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
  UNIQUE KEY uk_alias (alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='姓名别名';

CREATE TABLE user_account (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id   BIGINT       NOT NULL,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt 哈希',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  last_login_at DATETIME     NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_account_emp (employee_id),
  UNIQUE KEY uk_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录账号';

CREATE TABLE role (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  code            VARCHAR(32) NOT NULL,
  name            VARCHAR(64) NOT NULL,
  data_scope_type VARCHAR(16) NOT NULL COMMENT 'SELF DEPT DEPT_TREE ALL',
  remark          VARCHAR(255) NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE permission (
  id      BIGINT PRIMARY KEY AUTO_INCREMENT,
  code    VARCHAR(64) NOT NULL COMMENT '如 task:edit stats:view eval:view',
  name    VARCHAR(64) NOT NULL,
  module  VARCHAR(32) NOT NULL COMMENT 'ORG TASK TRK STAT KB CHAT SET',
  UNIQUE KEY uk_perm_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点';

CREATE TABLE role_permission (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  UNIQUE KEY uk_rp (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限';

CREATE TABLE user_role (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id BIGINT NOT NULL,
  role_id     BIGINT NOT NULL,
  UNIQUE KEY uk_user_role (employee_id, role_id),
  KEY idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员角色 一个可兼多角色';
