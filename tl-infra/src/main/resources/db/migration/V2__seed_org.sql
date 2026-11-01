-- 初始数据：一个根部门、两个试点部门、两个职位、一个管理员账号与四个角色
-- 管理员口令为 admin123，仅用于本地与演示；试点上线前在“账号设置”里改掉

INSERT INTO dept (id, parent_id, code, name, path, level, sort_no, status) VALUES
  (1, 0, 'HQ',   '总公司', '/1/',   1, 0, 1),
  (2, 1, 'PROD', '生产部', '/1/2/', 2, 1, 1),
  (3, 1, 'QA',   '质量部', '/1/3/', 2, 2, 1);

INSERT INTO position (dept_id, code, name, job_level, is_manager, sort_no) VALUES
  (1, 'GM',     '总经理',   9, 1, 0),
  (2, 'PROD01', '生产经理', 6, 1, 1),
  (2, 'PROD02', '班组长',   4, 1, 2),
  (3, 'QA01',   '质检员',   3, 0, 3);

INSERT INTO employee (id, emp_no, name, dept_id, position_id, status, join_date) VALUES
  (1, 'A0001', '张厚生', 1, 1, 1, '2020-01-01'),
  (2, 'A0002', '许伟',   2, 3, 1, '2021-03-01'),
  (3, 'A0003', '王先兵', 3, 4, 1, '2022-05-01');

UPDATE dept SET leader_employee_id = 1 WHERE id = 1;
UPDATE dept SET leader_employee_id = 2 WHERE id = 2;
UPDATE dept SET leader_employee_id = 3 WHERE id = 3;

INSERT INTO role (code, name, data_scope_type, remark) VALUES
  ('SYS_ADMIN', '系统管理员', 'ALL',       '账号、角色与配置'),
  ('MANAGER',   '管理者',     'ALL',       '全局视图与评价'),
  ('OWNER',     '负责人',     'DEPT_TREE', '本部门及下级派工与验收'),
  ('EXECUTOR',  '执行者',     'SELF',      '接单与打卡');

INSERT INTO permission (code, name, module) VALUES
  ('org:manage',  '组织维护',   'ORG'),
  ('task:create', '建单',       'TASK'),
  ('task:verify', '验收',       'TASK'),
  ('stat:view',   '统计查看',   'STAT'),
  ('kb:manage',   '知识库维护', 'KB'),
  ('sys:manage',  '系统管理',   'SET');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON r.code = 'SYS_ADMIN';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN ('stat:view')
WHERE r.code = 'MANAGER';

INSERT INTO user_account (employee_id, username, password_hash, status) VALUES
  (1, 'admin', '$2y$10$WnzsxbFxddpDbZQ4g5L/Oe75c.fiqWG70GCfz/6cNg2FQWQicav/y', 1);

INSERT INTO user_role (employee_id, role_id)
SELECT 1, id FROM role WHERE code = 'SYS_ADMIN';

INSERT INTO employee_alias (employee_id, alias, source) VALUES
  (1, '张总', 'MANUAL'),
  (2, '老许', 'MANUAL');
