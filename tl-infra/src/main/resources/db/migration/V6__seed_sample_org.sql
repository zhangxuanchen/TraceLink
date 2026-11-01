-- M2 组织扩充：多部门 + 样本工作日志里的真实人名与别名
-- 目的：让导入时人名匹配率有足够的分母（样本出现 15 个名字，M1 只有 3 人）

INSERT INTO dept (id, parent_id, code, name, path, level, sort_no, status) VALUES
  (4, 1, 'TECH', '技术部', '/1/4/', 2, 3, 1),
  (5, 1, 'MKT',  '市场部', '/1/5/', 2, 4, 1),
  (6, 1, 'FIN',  '财务部', '/1/6/', 2, 5, 1),
  (7, 1, 'HR',   '人事部', '/1/7/', 2, 6, 1),
  (8, 1, 'PUR',  '采购部', '/1/8/', 2, 7, 1);

-- 职位显式给 id：员工登记要用到 position_id，避免依赖自增顺序
INSERT INTO position (id, dept_id, code, name, job_level, is_manager, sort_no) VALUES
  (5,  1, 'VP',     '副总经理', 8, 1, 1),
  (6,  2, 'PROD03', '操作工',   2, 0, 3),
  (7,  2, 'PROD04', '设备维修', 3, 0, 4),
  (8,  3, 'QA02',   '质量经理', 5, 1, 5),
  (9,  4, 'TECH01', '技术经理', 6, 1, 6),
  (10, 4, 'TECH02', '工程师',   3, 0, 7),
  (11, 5, 'MKT01',  '市场经理', 6, 1, 8),
  (12, 5, 'MKT02',  '市场专员', 3, 0, 9),
  (13, 6, 'FIN01',  '财务经理', 6, 1, 10),
  (14, 6, 'FIN02',  '会计',     3, 0, 11),
  (15, 7, 'HR01',   '人事经理', 6, 1, 12),
  (16, 7, 'HR02',   '人事专员', 3, 0, 13),
  (17, 8, 'PUR01',  '采购经理', 6, 1, 14),
  (18, 8, 'PUR02',  '采购员',   3, 0, 15);

INSERT INTO employee (id, emp_no, name, dept_id, position_id, status, join_date) VALUES
  (4,  'A0004', '曹长坤', 2, 2,  1, '2019-06-01'),
  (5,  'A0005', '刘旭',   2, 3,  1, '2021-09-01'),
  (6,  'A0006', '倪伟杰', 2, 6,  1, '2022-03-01'),
  (7,  'A0007', '刘传胜', 2, 6,  1, '2022-03-01'),
  (8,  'A0008', '陈鸿飞', 2, 7,  1, '2020-11-01'),
  (9,  'A0009', '王源森', 8, 18, 1, '2021-07-01'),
  (10, 'A0010', '沈桦',   1, 5,  1, '2018-04-01'),
  (11, 'A0011', '孙雪娇', 3, 8,  1, '2022-08-01'),
  (12, 'A0012', '纪贤伟', 4, 10, 1, '2023-02-01'),
  (13, 'A0013', '董学军', 4, 9,  1, '2019-10-01'),
  (14, 'A0014', '李传礼', 5, 12, 1, '2023-05-01'),
  (15, 'A0015', '李春林', 6, 14, 1, '2021-12-01'),
  (16, 'A0016', '高璐',   7, 16, 1, '2022-06-01');

UPDATE dept SET leader_employee_id = 4  WHERE id = 2;
UPDATE dept SET leader_employee_id = 11 WHERE id = 3;
UPDATE dept SET leader_employee_id = 13 WHERE id = 4;
UPDATE dept SET leader_employee_id = 14 WHERE id = 5;
UPDATE dept SET leader_employee_id = 15 WHERE id = 6;
UPDATE dept SET leader_employee_id = 16 WHERE id = 7;
UPDATE dept SET leader_employee_id = 9  WHERE id = 8;

UPDATE employee SET manager_id = 4 WHERE id IN (5, 6, 7, 8);
UPDATE employee SET manager_id = 11 WHERE id = 3;
UPDATE employee SET manager_id = 13 WHERE id = 12;
UPDATE employee SET manager_id = 1 WHERE id IN (2, 10, 11, 14, 15, 16, 9);

-- 样本里的口语称呼，导入解析时按别名先匹配，再退化到姓名前缀
INSERT INTO employee_alias (employee_id, alias, source) VALUES
  (10, '老沈',   'MANUAL'),
  (4,  '曹经理', 'MANUAL'),
  (5,  '刘工',   'MANUAL'),
  (12, '小纪',   'MANUAL'),
  (6,  '小倪',   'MANUAL');
