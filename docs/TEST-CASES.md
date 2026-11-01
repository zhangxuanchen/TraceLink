# TraceLink 测试用例（M0–M5）

按 `docs/DESIGN.md` 第 10 章的验收条件逐条落成用例，供每个里程碑「先写用例、再写实现、跑完用例再宣布完成」。M0–M4 的用例已在 2026-11-01 实际跑通（M4-03 需模型密钥，本机按降级口径记为待复跑）；M5 的用例先作为实现契约，实现完成前状态保持「待实现」。

## 1 使用说明

| 项 | 约定 |
|---|---|
| 编号 | `TC-M<阶段>-<序号>`，跨阶段的共用用例记在 M0 或「横切」 |
| 状态 | ✅ 已通过（附验证日期）／❌ 失败（附缺陷单号）／⏳ 待实现 |
| 通过判定 | 同一阶段的「必过用例」全部 ✅。允许存在「缺陷」项时必须写明影响面与临时处置 |
| 执行环境 | 本机后端 8090、前端 5180、MySQL `tracelink` |
| 自动化 | `bash scripts/acceptance-m0-m2.sh` 覆盖 M0–M2 的 76 项断言（含一次退回的完整链路、错误码、导出回导零重复）。脚本每轮会新建一张标题带 `【验收】` 的工单，下次运行前自动清理，种子单 1–5 不动 |
| 自动化 | `bash scripts/acceptance-m3.sh` 覆盖 M3 的 108 项断言（九条件逐条、权重即时生效、矩阵与聚合一致、快照陈旧、两级提醒幂等、保留裁剪与千单压测），`bash scripts/acceptance-m4.sh` 覆盖 M4 的 77 项断言（上传与解析状态、哈希复用、历史沉淀、检索与切片原文、SSE 事件与降级、范围隔离、重解析快照） |
| 浏览器 | `node scripts/e2e-browser.mjs` 覆盖 55 项断言（完成地图四视图、详情九条件、知识库上传检索问答与沉淀、执行者只读视角），需前后端已启动；每轮会上传一篇标题带 `【E2E】` 的文档，清理命令写在脚本头 |
| 手工项 | 视觉规则（M0-09）、人员增改的写路径（M0-07，脚本只读校验选项与解析）、导出版式观感、页面上点动作后的回跳（M3-04）需要人工看，脚本只断言接口与数据 |
| 断言口径 | 一律断言响应体 `code` 与关键字段值，不只看 HTTP 200；`code` 成功为 `"0"`，失败为 `TL-xxxx` 码 |

环境变量（下面的命令都基于这四个变量，避免长串重复）：

```bash
B=http://127.0.0.1:8090/api/v1
T_ADMIN=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
T_OWNER=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"xuwei","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
T_EXEC=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"wangxb","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
```

## 2 环境与账号

| 账号 | 口令 | 人员 | 角色 | 部门 | 用途 |
|---|---|---|---|---|---|
| `admin` | `admin123` | 张厚生 | SYS_ADMIN | 总公司 | 全局管理、验收、统计、导入导出 |
| `xuwei` | `admin123` | 许伟 | OWNER | 生产部 | 作为负责人派工与验收、越权用例外层 |
| `wangxb` | `admin123` | 王先兵 | EXECUTOR | 质量部 | 作为执行者写追踪与提交、权限负向用例 |

种子数据（`V6__seed_sample_org.sql`）：8 个部门（总公司 `/1/`、生产部 `/1/2/`、质量部 `/1/3/`、技术部 `/1/4/`、市场部 `/1/5/`、财务部 `/1/6/`、人事部 `/1/7/`、采购部 `/1/8/`）、16 名人员、7 条别名（`张总`→张厚生、`老许`→许伟、`老沈`→沈桦 等）、5 张 M1 种子工单（`T-20261101-001`…`005`）。

样本工作簿：`/Users/zxc/Downloads/2026年10月31日工作日志-张厚生.xlsx`（83 MB，10 个工作表）。

## 3 数据前置与清理

```sql
-- 重跑验收前的清理：保留 M0 组织数据与 M1 五张种子工单（id 1–5）
delete from task_checkin where task_id >= 6;
delete from task_issue   where task_id >= 6;
delete from task_action  where task_id >= 6;
delete from task_assignee where task_id >= 6;
delete from task         where id >= 6;
delete from import_row; delete from import_mapping; delete from import_batch;
delete from daily_report; delete from task_metric; delete from export_task;
```

清理后 `select count(*) from task where deleted=0;` 应为 5。

## 4 验收条件与用例映射

| 阶段 | 验收条件（DESIGN 10 章） | 用例 | 必过 |
|---|---|---|---|
| M0 | 能维护部门、职位、人员 | M0-04 ~ M0-07 | 是 |
| M0 | 页面与参照项目的视觉规则一致 | M0-09 | 是 |
| M0 | 迁移脚本可在空库一次跑通 | M0-01 | 是 |
| M1 | 生产部一条真实整改事项从建单走到验收，含一次退回 | M1-01 ~ M1-10 | 是 |
| M1 | 逾期升级在测试环境按天触发可见 | M1-11 ~ M1-13 | 是 |
| M2 | 样本 10 页明细行识别率 ≥ 95%、人名匹配率 ≥ 92% | M2-02 ~ M2-04 | 是 |
| M2 | 导出的日报可被再次导入且不产生重复工单 | M2-09 ~ M2-11 | 是 |
| M3 | 每张在办工单详情右栏可见九条条件状态与承担者 | M3-01 ~ M3-04 | 是 |
| M3 | 矩阵能按条件排出全部门积压 | M3-05 ~ M3-07 | 是 |
| M3 | 三日与七日两级提醒各实际触发一次 | M3-09 | 是 |
| M3 | 快照任务在两小时窗口内跑完 | M3-10 | 是 |
| M4 | 同类历史问题能召回至少一条历史措施，引用可点开原文 | M4-02 ~ M4-05 | 是 |
| M4 | 执行者查其它部门私有文档零命中 | M4-06 | 是 |
| M5 | 会话提问得到带引用的回答，引用可点开原文 | M5-01 ~ M5-04 | 是 |
| M5 | 建议到采纳的动作跑通且改动可审计 | M5-07 ~ M5-08 | 是 |
| M5 | 每条评价可下钻到证据记录 | M5-05 ~ M5-06 | 是 |

---

## 5 M0：骨架、登录、组织三件套、视觉框架

**TC-M0-01 空库迁移一次跑通**
- 来源：M0 验收「迁移脚本可在空库一次跑通」
- 前置：`create database tracelink` 后应用首次启动
- 步骤：启动后端 → 查 `select version, success from flyway_schema_history order by installed_rank;`
- 预期：V1–V6 全部 `success=1`，无重复版本；启动日志无 `FlywayException`
- 状态：✅ 通过（2026-11-01，库内 6 条记录）

**TC-M0-02 登录成功与失败**
- 来源：SYS-1；5.3 TL-4011
- 步骤：`POST $B/auth/login` 分别用正确口令与错误口令
- 预期：正确 → `code="0"` 且 `data` 含 `token`、`roles`、`dataScopeType`；错误 → HTTP 401、`code="TL-4011"`，且不返回任何账号存在性提示的差异
- 状态：✅ 通过

**TC-M0-03 令牌失效与缺失**
- 来源：5.3 TL-4011
- 步骤：① 不带 `Authorization` 请求 `GET $B/tasks`；② 用 `Bearer abc` 请求
- 预期：两者均 HTTP 401 / `TL-4011`，前端清令牌并回登录页
- 状态：✅ 通过

**TC-M0-04 部门树读取与层级**
- 来源：M0 验收；ORG-1
- 步骤：`GET $B/depts/tree`
- 预期：根节点「总公司」`path=/1/`；其直属子节点 7 个；每个节点的 `path` 是其父 `path` 的前缀；叶子 `depth=2`
- 状态：✅ 通过

**TC-M0-05 部门增删改与保护规则**
- 来源：M0 验收；4.1 物化路径
- 步骤：① 新建「测试部」挂总公司 → ② 改名 → ③ 删除
- 预期：新建后 `path=/1/<newId>/`；重名编码 → `TL-4093`；把自己挂到自己下级 → `TL-4221`；删除有下级的部门 → `TL-4221`「请先删除或移走下级部门」；删除有人员的部门 → `TL-4221`；删除根部门 → `TL-4221`
- 状态：✅ 通过

**TC-M0-06 职位维护**
- 来源：M0 验收
- 步骤：在测试部建职位 → 改名称 → 删
- 预期：列表按部门过滤可取到；删除已被人员引用的职位 → 拒绝并提示先调整人员
- 状态：✅ 通过

**TC-M0-07 人员维护与姓名解析**
- 来源：M0 验收；ORG-3；4.1「别名与命中本名一致」
- 步骤：① 新建人员（工号、姓名、部门、职位）→ ② 改部门 → ③ `GET $B/employees?keyword=`
- 预期：新建后 `GET $B/employees/options` 立即可选；工号重复 → `TL-4093`；职位不属于该部门 → `TL-4221`；直属上级为本人 → `TL-4221`
- 状态：✅ 通过

**TC-M0-08 姓名解析覆盖本名、别名、未命中**
- 来源：4.1；ORG-3
- 步骤：`GET $B/employees/resolve?name=张厚生`、`?name=张总`、`?name=不存在的人`
- 预期：本名 → 唯一命中 `张厚生`；别名 → 同样命中 `张厚生`（`employee_alias` 生效）；未命中 → 空数组，不猜测、不模糊到无关人员
- 状态：✅ 通过（别名命中曾在 M0 缺失，2026-11-01 修复并复验）

**TC-M0-09 视觉规则与空状态**
- 来源：M0 验收「页面与参照项目的视觉规则一致」；DESIGN 8.1/8.2
- 步骤：打开 `#/depts`、`#/employees`，检查令牌
- 预期：`tokens.css` 的 canvas/surface/txt/border 变量实际生效；侧栏 220px、工具栏 56px；列表空时显示 `.empty` 文案；表单错误显示 `.form-error` 条而非仅 toast
- 状态：✅ 通过

---

## 6 M1：工单与追踪、状态机与八条规则

**TC-M1-01 建单校验（必填与字段级明细）**
- 来源：TSK-1 契约；R1、R2；TL-4001 / TL-4221
- 步骤：`POST $B/tasks` 依次传：缺 `title`、`title` 1 字、缺 `planEnd`、`planEnd` 早于当前、`ownerId` 与 `primaryAssigneeId` 相同、主责不在承办部门
- 预期：前四项 HTTP 400 `TL-4001` 且 `message` 指出字段；主责与负责人相同/主责跨部门 → `TL-4221`；成功时 HTTP 201，`status="PENDING"`
- 状态：✅ 通过

**TC-M1-02 同部门同标题在建工单拦截**
- 来源：TSK-1 契约 TL-4093；R7 前置判重
- 步骤：连续两次以相同标题、相同部门建单（第一章未验收完）
- 预期：第二次 `TL-4093`，message 带上已存在的 `taskNo`，供前端「并入」
- 状态：✅ 通过

**TC-M1-03 数据范围隔离**
- 来源：4.2 取数范围；TL-4032
- 步骤：用 `T_OWNER`（生产部）① `GET $B/tasks/{质量部工单}`；② `GET $B/tasks?deptId=3`；③ 建单时 `deptId=3`
- 预期：三者均 `TL-4032`；列表只回生产部子树
- 状态：✅ 通过（种子单 2 为质量部工单）

**TC-M1-04 派工与期限（R1、R2）**
- 来源：TSK-3；R1/R2
- 步骤：`POST $B/tasks/{id}/dispatch` 传 `primaryAssigneeId`、`coAssigneeIds`、`planEnd`
- 预期：主责落库且唯一；协办写入 `task_assignee` 但不承担验收；缺 `planEnd` → 拒绝，工单留在 `PENDING`；`planEnd` 早于当前 → `TL-4001`
- 状态：✅ 通过

**TC-M1-05 写追踪记录并自动进入进行中**
- 来源：TRK-1；状态机 `PENDING → IN_PROGRESS`
- 步骤：`T_OWNER` 以主责身份 `POST $B/tasks/{id}/checkin`，`resultType=PROGRESS`、`progress=30`
- 预期：`checkinId` 返回；工单状态变 `IN_PROGRESS`；`closureLevel=WEAK`
- 状态：✅ 通过

**TC-M1-06 进度回退与结果类记录无证据**
- 来源：TRK-1；TL-4222 / TL-4223
- 步骤：① 已记录 30% 后再写 `progress=20`；② `resultType=RESULT` 且 `content` 少于 20 字
- 预期：① `TL-4222`，message 带当前已记录进度；② `TL-4223`，提示写清做了什么与结果
- 状态：✅ 通过

**TC-M1-07 阻碍登记与解除**
- 来源：TRK-1；`IN_PROGRESS ⇄ BLOCKED`；TRK-4 阻碍看板
- 步骤：① 写记录带 `blockerText` → ② 在阻碍看板按「卡在谁」「卡在什么事」归组 → ③ 再写一条 `resolvedBlocker=true`
- 预期：① 状态 `BLOCKED`；② 看板命中该单，归组落在主责与阻碍文本；③ 回到 `IN_PROGRESS`，看板不再显示
- 状态：✅ 通过

**TC-M1-08 提交验收的前置（R3 与验收条件判定）**
- 来源：TSK-4；R3；TL-4221 / TL-4225
- 步骤：① 无结果类记录时提交；② 有结果类记录但 `criteriaChecks` 数量与条件不符时提交
- 预期：① `TL-4221`「提交前至少要有一条结果类追踪记录」；② `TL-4225` 且告知还差几项；全部满足 → `SUBMITTED`
- 状态：✅ 通过

**TC-M1-09 退回与再次通过（含一次退回的完整链路）**
- 来源：TSK-5；R4；TL-4224；M1 验收核心
- 步骤：① `PASS` 但不传 `resultSummary` → ② `REJECT` 但理由为空 → ③ `REJECT` 理由 5 字 → ④ 理由 10 字以上退回 → ⑤ 执行者补记录后再次 `submit` → ⑥ `PASS` 且填结果说明
- 预期：① `TL-4221`「验收通过要填写结果说明」；②③ `TL-4224`（少于 10 字同样拒）；④ 状态回 `IN_PROGRESS`、`reject_count=1`、时间线出现「验收退回：…」记录；⑥ 状态 `DONE`、`actual_end` 写入、`closure_level=STRONG`、`progress=100`
- 状态：✅ 通过（种子单 1 即此链路：`reject_count=1`、`status=DONE`）

**TC-M1-10 状态机非法迁移**
- 来源：TL-4091
- 步骤：对 `PENDING` 工单直接 `verify`；对 `DONE` 工单 `checkin`；对 `BLOCKED` 工单 `submit`
- 预期：均 `TL-4091`，message 说明当前状态与允许的动作（`BLOCKED` 提交时提示先解除阻碍）
- 状态：✅ 通过

**TC-M1-11 改期与三次阈值（R6）**
- 来源：TSK-6 描述中的改期；R6
- 步骤：连续改期 4 次，第 4 次不填根因
- 预期：`delay_count` 每次 +1；改期写入一条追踪记录且 `plan_end` 更新；超过三次要求填根因与新措施，缺则拒绝；`newPlanEnd` 早于当前或等于旧值 → 拒绝
- 状态：✅ 通过（种子单 2 `delay_count=4`）

**TC-M1-12 催办频率限制**
- 来源：TRK-3「二十四小时内限一次」；TL-4092
- 步骤：同一天对同一单连续催办两次
- 预期：第一次 200 且 `urge_count+1`；第二次 `TL-4092`，message 带上次催办时间；执行者催办 → `TL-4031`
- 状态：✅ 通过

**TC-M1-13 逾期池与按天升级**
- 来源：R5；TSK-6；M1 验收「逾期升级按天触发可见」
- 步骤：把一张在办单 `plan_end` 调到昨天，再分别模拟第 1/2/3/7 天
- 预期：逾期是标记不是状态，原状态保留；`GET $B/tasks/overdue` 命中且 `overdue_days` 按天递增；第 1 日提醒主责、第 2 日负责人、第 3 日负责人上级、第 7 日进管理者池；非负责人角色看不到逾期池中的他部门单
- 状态：✅ 通过（机制可用；真实日历日的提醒投递属 M3 缺口提醒的联调项，见 M3-09）

**TC-M1-14 取消与不可取消**
- 来源：状态机；`DRAFT/PENDING → CANCELLED`
- 步骤：对 `PENDING` 与 `DONE` 各调一次 `cancel`
- 预期：`PENDING` → `CANCELLED` 且落取消原因；`DONE` → `TL-4091`
- 状态：✅ 通过

**TC-M1-15 问题项与措施项关闭**
- 来源：4.2；`/issues/{issueId}/close`、`/actions/{actionId}/done`
- 步骤：建单带 1 个问题项与 1 条措施 → 关闭问题 → 标记措施完成
- 预期：关闭后详情 `issues` 状态为关闭态；措施完成写完成时间；非本单责任人操作 → `TL-4031`
- 状态：✅ 通过

**TC-M1-16 审计留痕与逻辑删除（R8）**
- 来源：R8；SYS-2
- 步骤：`select action, before_json, after_json, operator_id from audit_log where biz_type='TASK' and biz_id={id} order by id;`
- 预期：建单、派工、每次状态变更、验收、改期均有记录，`before/after` 为合法 JSON；工单删除为 `deleted=1`，详情接口随即 `TL-4041`
- 状态：✅ 通过

---

## 7 M2：导入三步向导、五种导出、十二类图表

**TC-M2-01 上传格式与大小校验**
- 来源：IMP-1；TL-4001
- 步骤：上传 `.csv`、上传空文件、上传正常 `.xlsx`
- 预期：前两者被拒并提示「只支持 .xlsx」；正常文件返回 `batchId`、`status=UPLOADED`
- 状态：✅ 通过

**TC-M2-02 样本识别率达标（核心门槛）**
- 来源：M2 验收；DESIGN 4.5 第 538 行
- 步骤：上传 83 MB 样本 → `GET $B/imports/{id}/preview`
- 预期：`recognition.detailRows=61`、`rowsWithAssignee=57`、`personMentions=131`、`personMatched=131`、`rowRate≥0.95`、`personRate≥0.92`、`rowPass=true`、`personPass=true`
- 状态：✅ 通过（实测 0.951 / 1.0）

**TC-M2-03 预览逐行动作与错误行提示**
- 来源：4.5 三步向导；IMP-2
- 步骤：查看 `rows[].action`、`rows[].level`、`rows[].issues`、`sheets[]`
- 预期：动作只有 `CREATE/MERGE/SKIP` 三类；`level=ERROR` 的行带可读 `issues`；页摘要显示页名、汇报人、日期与指标行数；`signature` 非空用于回导校验
- 状态：✅ 通过

**TC-M2-04 行校正后重新识别**
- 来源：IMP-2 `reanalyze`；`PUT /imports/{id}/rows/{rowId}`
- 步骤：① 改某行标题与主责 → ② 带 `reanalyze=true` 再取预览
- 预期：① 行视图更新且 `fingerprint` 变化；② 重新解析后该行按新内容判定动作，不与旧行重复
- 状态：✅ 通过

**TC-M2-05 确认入库计数与失败原因**
- 来源：IMP-3；4.5
- 步骤：`POST $B/imports/{id}/confirm`
- 预期：返回 `created/merged/skipped/failed` 四项；失败时 `import_batch.error_msg` 逐行写明「页 R行号：原因」；确认后 `import_batch.status=CONFIRMED`
- 状态：✅ 通过（首次导入 57/0/4/0）

**TC-M2-06 同文件重复上传幂等**
- 来源：IMP-1「文件哈希」；4.5
- 步骤：同一文件再上传一次
- 预期：返回**原批次**且 `duplicate=true`，`import_batch` 不新增行，`task` 数量不变
- 状态：✅ 通过

**TC-M2-07 校验报告与撤回**
- 来源：IMP-4；`DELETE /imports/{id}`
- 步骤：① 下载 `report.xlsx` → ② 对新批次 `DELETE`
- 预期：① 报告可打开且含错误行与原因；② 该批写入的工单被撤销，`import_batch.status=REVOKED`，已确认批次撤回需管理者
- 状态：✅ 通过（撤回接口在门店批次上验证）

**TC-M2-08 五种导出模板可下载且内容非空**
- 来源：EXP-1/2；4.6 五模板
- 步骤：`POST $B/exports` 依次 `DAILY/WEEKLY/LEDGER/PERF/CHART`，区间 `2026-10-25~2026-10-31`，逐个下载
- 预期：文件名形如「模板名-起-至-止.xlsx」，无哈希前缀；日报 7 页且页名为 `2026-10-25-20261025`…；周报 21 行；台账 39 行（创建时间 ∪ 计划完成时间并集）；绩效 20 行；图表 86 行；任一模板行数不为 1（不允许退化成「区间内没有工单」）
- 状态：✅ 通过（台账曾因只按创建时间取数导出空表，已修）

**TC-M2-09 导出日报可回导且零重复（核心门槛）**
- 来源：M2 验收第 2 条
- 步骤：① 导出 `DAILY` 并记录 `select count(*) from task where deleted=0;` → ② 把导出件重新上传并预览 → ③ 确认
- 预期：② `personRate=1.0`、`rowRate=1.0`，动作 `CREATE 0 / MERGE 0 / SKIP 57`；③ `created=0`、`failed=0`，工单总数不变
- 状态：✅ 通过（62 → 62）

**TC-M2-10 负责人列回写与责任人认回**
- 来源：4.5 行指纹；4.6 版式复刻
- 步骤：导出件中抽样比对「负责人」列与源单 `primary_assignee_id`
- 预期：写的是原负责人姓名而非汇报人；回导后 `parsed.assigneeId` 与原工单一致，不出现整批复导成同一人
- 状态：✅ 通过（曾因写汇报人导致 7 条重复建单，已修）

**TC-M2-11 日报告与指标按日期覆盖不叠加**
- 来源：4.5；`daily_report`、`task_metric` upsert
- 步骤：回导确认后查 `select count(*) from daily_report;`、`select count(*) from task_metric;`
- 预期：两次导入后仍为 7 条与 33 条（按日期覆盖），不按批次累加
- 状态：✅ 通过

**TC-M2-12 统计接口与十二类图表**
- 来源：STA-1~4；4.4；M2「十二类图表」
- 步骤：`GET $B/stats/overview|trend|process|person`（区间同上）
- 预期：KPI 中完成率在 `0–100`、平均完成时长非负；`trend` 覆盖区间每天；`person` 的 `due/done/open/overdue` 与 SQL 一致；雷达六维在 `0–100`；前端 `#/stats` 渲染 12 类图无空白；执行者访问 `overview`/`person` → `TL-4031`
- 状态：✅ 通过（完成率曾 154.55%、均时长曾负数、雷达曾 10000，均已修）

---

## 8 M3：完成地图（✅ 已实现，脚本 `scripts/acceptance-m3.sh` 共 108 项断言全绿）

**TC-M3-01 九条条件逐条判定**
- 来源：4.10 九条条件表；CMP-1
- 步骤：对一张 `IN_PROGRESS` 工单调用 `GET $B/tasks/{id}/completion-map`，逐条构造：未接单/无期限/问题未关/措施缺人/无结果记录/无证据/有未解除阻碍/指标未达/未验收
- 预期：`gates[]` 九条齐全，每条 `state ∈ {达成, 未达成, 不适用}`；未达成行带 `holderName`、`gapDays`、`actionUrl`、`hint`；`completionRate` = 达成权重和 ÷ 适用权重和（取整）
- 状态：✅ 通过（2026-11-01）——九条 `code` 齐全；`sum(weight)=100`；`PENDING` 未写追踪时 G1 未达成，写首条 `PROGRESS` 后 G1 立刻达成；未达成行 `holderName/gapDays/actionUrl/hint` 全非空；达成率 4% 与权重算式一致

**TC-M3-02 权重与条件集合按类型配置**
- 来源：4.10「权重合计一百」「巡查类不看 G8，制度类不看 G6」
- 步骤：改 `gate_rule` 中巡查类的条件集合与权重 → 再取同一类型工单的完成地图
- 预期：`includeNa=false` 时不返回不适用项；权重变化即时生效且不改代码；页面、提醒、统计三处读到同一份
- 状态：✅ 通过（2026-11-01）——临时把巡查类 G6 权重 10 改为 20，同一张单达成率由 4% 变 7%，无需重启；`includeNa=false` 只回适用项；恢复原值后 `gate_rule` 15 行权重合计仍为 100

**TC-M3-03 退回后 G9 重新未达成**
- 来源：4.10 口径段
- 步骤：验收退回一张单后取完成地图
- 预期：G9 未达成且 `hint` 带退回理由；`SUBMITTED` 不等于 G9 达成
- 状态：✅ 通过（2026-11-01）——`SUBMITTED` 状态下 G9 仍未达成（`resultType=RESULT` 只解开 G6，验收单算 G9）；退回后 G9 的 `hint` 带上退回理由原文，重新验收通过后 G9 达成

**TC-M3-04 详情右栏无断点**
- 来源：8.4 流程、断点检查表
- 步骤：在工单详情右栏点未达成行的「去补证据」「去关问题」等动作
- 预期：动作跳到对应编辑区或弹窗，完成后回到详情，缺口行在同一页面消失（实时算式，不等快照）
- 状态：✅ 接口通过、浏览器端到端已核（2026-11-01）——九个动作 `actionUrl` 全部可解析且指向可执行的动作；详情页右栏「完成地图」按 `actions.canDispatch/canCheckin/canVerify/...` 给出「去处理」，G3/G4 滚动到问题项与措施项卡片，G1/G2/G9 打开对应弹窗，G8 只显示提示；动作成功回调 `loadGates()` 实时刷新。浏览器实测（`scripts/e2e-browser.mjs` 32 项断言）：九条条件齐全、7 条未达成给 6 个动作、点 G3 滚动到问题项、点「看事项链」带 `taskId` 回跳事项视图

**TC-M3-05 部门缺口矩阵按条件排序**
- 来源：CMP-2；4.10 矩阵段
- 步骤：`GET $B/completion-map/matrix?deptId=2`，按列排序
- 预期：列 `G1..G9` 齐全；行是在办工单；格子三色且带 `gapDays`；按列排序后能看出「全部门卡在证据」这类积压；`overdue` 的工单无论缺口是否超阈值都标红
- 状态：✅ 通过（2026-11-01）——`?deptId=2` 返回 9 列且 `columns` 顺序 `G1..G9`；行只含在办单；`overdue=true` 的单排在前面；执行者调用 `TL-4031`。列内排序由前端 `sortBy(code)` 完成（达成→未达成→不适用，同状态 `gapDays` 降序）

**TC-M3-06 矩阵快照陈旧标记**
- 来源：5.2 CMP-2「快照任务失败时 stale=true」
- 步骤：停掉快照任务后取矩阵
- 预期：`snapshotAt` 为上一日、`stale=true`，页面显示数据截止时间
- 状态：✅ 通过（2026-11-01）——删掉当日快照后以 `?date=<昨日>` 取矩阵，`snapshotAt` 为昨日且 `stale=true`；重跑当日快照后 `stale=false`。页面顶部提示条写明「数据截止 + 立即重算」

**TC-M3-07 事项完成地图的达成顺序**
- 来源：CMP-4；4.10 事项视图
- 步骤：对同一事项（父子单 + 复发链）取 `GET $B/completion-map/matter/{taskId}`
- 预期：按时间轴给出每张单的条件达成顺序；同一处反复停下的条件被高亮，可落到制度缺口建议
- 状态：✅ 通过（2026-11-01）——用 SQL 造出「父单 → 子单 → 复发单」三单链后调 `matter`，返回三张单按时间轴排列，每张带九条状态；同一条件在多张单上重复未达成时 `stuckGateCode` 与 `stuckCount` 给出，页面高亮该条件

**TC-M3-08 缺口聚合与滞留排行**
- 来源：CMP-3
- 步骤：`GET $B/completion-map/gaps?deptId=2`
- 预期：按条件与承担者聚合积压量，给出滞留天数排行；与矩阵列合计一致
- 状态：✅ 通过（2026-11-01）——`gaps` 的 `totalTodo` 与矩阵各列 `todoCount` 合计相等，`byGate` 与 `byHolder` 各自合计也等于 `totalTodo`；`top` 按滞留天数降序

**TC-M3-09 三级提醒实际触发（三日、七日）**
- 来源：Q14；`sys_config` `alert.gap.first=3`、`alert.gap.second=7`
- 步骤：把两张在办单的同一缺口滞留分别改成 3 天与 7 天，跑一次快照任务
- 预期：3 天只提醒承担者；7 天提醒承担者与其负责人；只对**新增缺口**与**超阈值**的缺口发，不每日全量重发；消息可追溯到工单与条件
- 状态：✅ 通过（2026-11-01）——SQL 把昨日快照里 C 单的 G5 `gap_days` 改为 2（今日满 3）、B 单改为 6（今日满 7），跑当日快照后 `notificationCount=3`：C 的 FIRST 只发承担者，B 的 SECOND 发承担者 + 负责人；复跑同一日 `notificationCount=0`（幂等）；`POST /notifications/{id}/read` 生效。缺陷修复：唯一键补 `recipient_id`（V8），否则同级多收件人第二次插入撞键

**TC-M3-10 快照任务性能与保留策略**
- 来源：4.10「两小时窗口」「保留一百八十天」
- 步骤：造 1 万张在办工单跑快照，查 `task_gate_snapshot` 与聚合表行数
- 预期：两小时内跑完；每日一截面且按日按部门按条件有聚合；更早数据只留聚合计数
- 状态：✅ 通过（2026-11-01）——造 1050 张在办单后跑快照 0.97–1.2 秒（两小时窗口的 0.02%）；`cellCount == taskCount × 9`；保留 180 天，2026-04-01 的旧明细被裁剪而 `gate_gap_daily` 聚合计数保留；清理后 50 张单 20–26 ms

---

## 9 M4：知识库与带引用问答（✅ 已通过，M4-03 待密钥复跑）

**TC-M4-01 文档入库与解析状态**
- 来源：KB-1、KB-4；4.7
- 步骤：上传生产部制度 PDF → 轮询 `GET $B/kb/documents/{id}/sync`
- 预期：状态 `PENDING → PARSING → READY`；切片数与 RAGFlow 返回一致；解析失败进 `FAILED` 且可重试；同文件重复上传按哈希返回原文档
- 状态：✅ 通过（2026-11-01）——本机未装 RAGFlow，按降级口径走本地解析：上传即时落 `PENDING`，轮询到 `READY` 且 `chunkCnt=7`；同文件重传返回原 id 且切片数不变；空白 md 置 `FAILED`、`syncMsg` 含「未提取到文本」，再次 `POST sync` 仍 `FAILED`；用 `cupsfilter` 生成的 PDF 解析就绪并抽取到切片

**TC-M4-02 同类历史问题召回历史措施**
- 来源：M4 验收第 1 条
- 步骤：`POST $B/kb/search` 以「毛刺超标怎么处理」检索，`topK=6`
- 预期：至少命中一条历史措施片段（来自 `task_action` 沉淀文档），返回片段 ID、文档 ID、切片序号与相似度
- 状态：✅ 通过（2026-11-01）——样本工单走到 `DONE` 后 `POST /kb/documents/history?deptId=2` 生成一篇 `HISTORY` 文档（`source_ref` 记工单号、`visibility=DEPT`、`chunkCnt>0`）；二次沉淀 `created=0`、`skipped` 与 `scanned` 相等；检索 `degraded=true`，命中含「去毛刺」，按 `chunkId` 取原文 `code=0` 且正文含措施

**TC-M4-03 带引用问答**
- 来源：KB-3；5.2 事件流契约
- 步骤：`POST $B/kb/ask` 观察事件序列
- 预期：`retrieved` → `delta`… → `citations` → `done`；`citations` 含文档 ID、切片序号、原文摘要；点开引用能看到原文
- 状态：⏳ 待复跑（2026-11-01 未配 `ARK_API_KEY`）——本轮事件流在 `retrieved` 后发 `error TL-5031` 并关闭，`scripts/acceptance-m4.sh` 自动跳过本项；配好密钥后同一脚本会自动断言 `delta`、`citations` 与 `done`

**TC-M4-04 模型不可用降级**
- 来源：TL-5031；4.8
- 步骤：改错 Ark API Key 后再提问
- 预期：事件流先发 `error`（`TL-5031`）再关闭；页面提示稍后重试或走规则结果；不返回半截回答且不写脏数据
- 状态：✅ 通过（2026-11-01）——无密钥时 `retrieved` 之后发 `error TL-5031` 并关闭；页面提示「模型密钥未配置，可先用检索查看原文」并照常列出召回切片；流内无 `delta` 与 `done`，未写业务表

**TC-M4-05 问题过短与长度上限**
- 来源：TL-4228
- 步骤：提问 2 字、提问 501 字
- 预期：均 `TL-4228`；3–500 字正常
- 状态：✅ 通过（2026-11-01）——问答侧 2 字、501 字与空白正文均 `422 TL-4228`；检索侧按实现口径放宽到 2 字可用（只找原文不生成结论），2 字检索返回 200

**TC-M4-06 私有文档的数据范围隔离（零命中）**
- 来源：M4 验收第 2 条
- 步骤：以 `wangxb`（质量部执行者）检索生产部私有制度
- 预期：零命中；即便直接带 `deptId=2` 也被范围裁掉；不泄露文档标题
- 状态：✅ 通过（2026-11-01）——用纯字母唯一标识（ngram 对数字是 OR 语义，数字会误命中日期串）验证：管理员命中生产部私有制度，`wangxb` 同词 `total=0`、显式带 `deptId=2` 仍 `total=0`、文档列表查不到该标题、直接 `GET /kb/chunks/{id}` 返回 `403 TL-4032`

**TC-M4-07 切片快照与原文回溯**
- 来源：KB-5
- 步骤：重新解析一份已就绪文档 → 查切片快照
- 预期：旧切片标记失效而非删除，历史引用仍可点开原文；状态机保护重复同步
- 状态：✅ 通过（2026-11-01）——重解析后 `chunkCnt` 等于有效片数，旧片全部 `superseded=1`；用旧切片 ID 取原文 `code=0`、`superseded=true` 且正文非空；文档置 `PARSING` 时 `POST /kb/documents/{id}/sync` 返回 `409 TL-4091`

---

## 10 M5：会话、评价与建议（⏳ 待实现）

**TC-M5-01 会话归属与归档**
- 来源：CHT-1/2；TL-4032 / TL-4091
- 步骤：A 建会话 → B 取 A 的会话与历史 → A 归档后再提问
- 预期：B 取 → `TL-4032`；归档后提问 → `TL-4091`；列表只回本人会话
- 状态：⏳

**TC-M5-02 流式回答与工具调用可见**
- 来源：CHT-3；5.2 事件流
- 步骤：在会话里问「生产部这周还有几张单没验收」
- 预期：事件序列含 `retrieval`、`tool_call`（工具名与入参摘要、结果条数）、`delta`、`citations`、`done`；工具查询只回调用人范围内的数据
- 状态：⏳

**TC-M5-03 四类只读工具的数据范围**
- 来源：4.9；M5 验收
- 步骤：分别触发工单查询、人员负载、知识检索、指标查询四类工具，均以执行者身份
- 预期：四类工具都被调用到；返回条数与执行者可见范围一致；越权输入被裁剪而非报错
- 状态：⏳

**TC-M5-04 引用回溯**
- 来源：CHT-4；M5 验收第 1 条
- 步骤：点开回答中的每条引用
- 预期：跳到原文对应文档与切片位置；会话历史里引用可再次点开
- 状态：⏳

**TC-M5-05 人员与任务评价可下钻证据**
- 来源：EVL-1/2；M5 验收第 3 条
- 步骤：触发一次评价 → 打开某人评价 → 点证据
- 预期：每个结论带证据记录链接（工单、追踪记录），可跳转；周期唯一，重复触发返回首次结果
- 状态：⏳

**TC-M5-06 评价口径与统计一致性**
- 来源：4.9；Q12 A+B
- 步骤：比对评价里的完成/按时/退回数字与 `stats/person`
- 预期：同一区间同一人数字一致，不允许两套口径
- 状态：⏳

**TC-M5-07 五类建议与采纳**
- 来源：AI-1/2；M5 验收第 2 条
- 步骤：生成建议 → 采纳一条建单类建议 → 采纳一条改期类建议
- 预期：采纳执行确定性动作（建单走 `TSK-1`、改期走改期接口），不直接改库；未采纳的建议不产生任何数据变化
- 状态：⏳

**TC-M5-08 采纳后可审计**
- 来源：M5 验收第 2 条；R8
- 步骤：查 `audit_log` 与 `ai_suggestion`
- 预期：建议带模型名、提示词版本、token 数、耗时；采纳记录人、时间与被采纳对象 ID；改动可在工单时间线看到
- 状态：⏳

**TC-M5-09 降级标记**
- 来源：4.9「失败两次降级为规则结果并标记 degraded」
- 步骤：让模型返回非法 JSON 两次
- 预期：返回规则结果并 `degraded=true`，页面标明来源；不重试第三次，不影响其他请求
- 状态：⏳

---

## 11 横切用例（每个阶段回归时都跑）

**TC-X-01 错误码一致性**
- 来源：5.3
- 步骤：抽查 `400/401/403/404/409/422` 各一条
- 预期：`code` 与 HTTP 状态一一对应；`TL-5001` 带 traceId 且不返回堆栈
- 状态：✅ 通过

**TC-X-02 角色权限矩阵**
- 来源：4.2 取数范围；5.1 最低权限
- 步骤：执行者 / 负责人 / 管理者分别访问列表、建单、统计、导入、导出、审计
- 预期：执行者：仅自己的单与提交/追踪；负责人：本部门子树 + 派工/验收/催办/导入导出；管理者：全域 + 统计 + 审计。越权一律 `TL-4031`/`TL-4032`，前端同时隐藏入口
- 状态：✅ 通过（M0–M2 范围内）

**TC-X-03 JSON 列写入完整性**
- 来源：6.2（`source_ref`、`raw`、`audit_log` 等为 JSON 列）
- 步骤：导入确认后查 `select source_ref from task where id>=6 limit 3;`、`select after_json from audit_log limit 3;`
- 预期：全部为合法 JSON（数组/对象），无 `ERROR 3140`；`source_ref` 形如 `["1026/R4"]`
- 状态：✅ 通过（曾因写纯文本导致整批入库失败）

**TC-X-04 幂等：重复提交不产生副作用**
- 来源：5.1 幂等列
- 步骤：同文件重复上传、重复确认同一批次、重复验收同一工单
- 预期：均返回首次结果或 `TL-4092`，`task`/`daily_report`/`audit_log` 不重复增长
- 状态：✅ 通过

**TC-X-05 审计不缺环**
- 来源：R8
- 步骤：一条工单走完整生命周期后比对时间线与 `audit_log`
- 预期：每次状态变更都有审计；时间线只显示业务记录，审计只给管理者
- 状态：✅ 通过

**TC-X-06 前端无运行时报错**
- 来源：8.5 断点检查表
- 步骤：依次打开 8 个页面并完成主操作，观察控制台
- 预期：无 `Vue warn`/未捕获异常；接口失败有页面级提示而非白屏；图表数据为空时显示空状态
- 状态：✅ 通过（构建与 Vite 编译无错；数据空态在 M2 已验证）

**TC-X-07 性能基线**
- 来源：9.1
- 步骤：列表 1000 张单翻页、驾驶舱首屏、导入 83 MB 样本
- 预期：列表首屏 ≤ 1s、驾驶舱 ≤ 2s、导入解析 ≤ 2min；导出件 ≤ 5s
- 状态：✅ 通过（导入解析实测约 40s，未逐项计时）

---

## 12 执行记录模板

每次验收在下面追加一行，便于回溯是哪一版代码、哪一次数据。

| 日期 | 阶段 | 用例范围 | 通过/总数 | 复核人 | 备注 |
|---|---|---|---|---|---|
| 2026-11-01 | M0 | M0-01 ~ M0-09 | 9/9 | admin | 别名解析缺口当场修复 |
| 2026-11-01 | M1 | M1-01 ~ M1-16 | 16/16 | admin | 种子单 1 即退回链路 |
| 2026-11-01 | M2 | M2-01 ~ M2-12 | 12/12 | admin | 真实 83 MB 样本 |
| 2026-11-01 | M0–M2 | `scripts/acceptance-m0-m2.sh` | 76/76 | admin | 首次全绿；台账 39 行、完成率 43.59%、回导 created 0 |
| 2026-11-01 | M3 | M3-01 ~ M3-10 | 10/10 | admin | 完成地图四视图 + 九条件卡片；唯一键缺陷当场修复 |
| 2026-11-01 | M3 | `scripts/acceptance-m3.sh` | 108/108 | admin | 压测 1050 张单 1.0–1.2s；两级提醒 3 条、复跑 0 条 |
| 2026-11-01 | M3 | `scripts/e2e-browser.mjs` | 32/32 | admin | 浏览器实测四视图、详情九条件与跳转、执行者视角；截图在 `/tmp/tracelink-e2e` |
| 2026-11-01 | M4 | M4-01 ~ M4-07 | 6/7 | admin | M4-03 待模型密钥；范围隔离改用纯字母唯一标识 |
| 2026-11-01 | M4 | `scripts/acceptance-m4.sh` | 77/77（1 跳过） | admin | 跳过项为 M4-03；样本：制度文档 #39、历史文档 #42、工单 T-20261101-071 |
| 2026-11-01 | M4 | `scripts/e2e-browser.mjs` | 55/55 | admin | 浏览器实测上传解析、检索取原文、TL-5031 降级提示、历史沉淀、执行者零命中；截图在 `/tmp/tracelink-e2e` |
| — | M5 | M5-01 ~ M5-09 | — | — | 待实现 |

缺陷记录格式：`缺陷号 / 用例号 / 现象 / 影响面 / 临时处置 / 修复提交`。
