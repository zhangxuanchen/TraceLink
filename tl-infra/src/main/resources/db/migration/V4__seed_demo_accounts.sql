-- 演示账号：给负责人与执行者各一个登录，便于按角色走通工单链路
-- 口令与 admin 相同（admin123），仅用于本地与演示；试点上线前改掉

INSERT INTO user_account (employee_id, username, password_hash, status) VALUES
  (2, 'xuwei',  '$2y$10$WnzsxbFxddpDbZQ4g5L/Oe75c.fiqWG70GCfz/6cNg2FQWQicav/y', 1),
  (3, 'wangxb', '$2y$10$WnzsxbFxddpDbZQ4g5L/Oe75c.fiqWG70GCfz/6cNg2FQWQicav/y', 1);

INSERT INTO user_role (employee_id, role_id)
SELECT 2, id FROM role WHERE code = 'OWNER';

INSERT INTO user_role (employee_id, role_id)
SELECT 3, id FROM role WHERE code = 'EXECUTOR';
