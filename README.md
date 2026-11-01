# TraceLink · 企业执行追踪与工单管理系统

把散落在 Excel 日报、群聊截图和口头交办里的事，变成有唯一主责、有期限、有过程记录、有验收结果的工单流，并让每张工单都能回答"还差什么"。

- 技术栈：Spring Boot 3.x · AgentScope(Java) · Maven 多模块 · MySQL 8 · Vue3 + 自研 SVG 图表组件（12 类图表不依赖图表库）
- 检索底座：RAGFlow（解析、切片、向量、混合检索与重排）；本轮不引入 Dify
- 三类用户：管理者（看全局与收敛）、负责人（派工与验收）、执行者（接单与打卡）
- 视觉基准：`Memory-Observatory/memory-observatory`（浅色单色系、高信息密度、语义色克制）
- 模型服务：火山引擎 Ark（对话在应用侧，向量与重排在 RAGFlow 侧），配置见 `docs/LLM-CONFIG.md`

## 文档

| 文档 | 内容 |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | 设计文档 v1.1：文档分类与已确认决策（Q1–Q15 全部落定） → 样本实测结构与设计约束 → 系统架构与选型理由 → 模块设计（含 4.10 完成地图） → 接口契约与错误码 → ER、35 张表结构与逐字段说明、索引与一致性 → 运行与失败处理 → 非功能设计 → 前端视觉、页面一览、通用交互规则、十个关键流程、断点检查表 → M0–M5 与验收 → 确认项收口；M1–M4 的验收记录在 11 章 |
| [docs/TEST-CASES.md](docs/TEST-CASES.md) | 验收用例 M0–M5：每条验收条件对应到可执行的 `TC-Mx-nn`，含前置、步骤、断言口径与执行记录；M0–M5 已跑通（M4-03/M5-09 的模型路径待 `ARK_API_KEY` 复跑） |
| [scripts/acceptance-m0-m2.sh](scripts/acceptance-m0-m2.sh) | M0–M2 一键验收：76 项断言，含建单到验收一次退回、错误码、导入导出回导零重复 |
| [scripts/acceptance-m3.sh](scripts/acceptance-m3.sh) | M3 一键验收：108 项断言，含九条条件逐条、权重即时生效、矩阵与聚合一致、快照陈旧、两级提醒幂等、保留裁剪与千单压测 |
| [scripts/acceptance-m4.sh](scripts/acceptance-m4.sh) | M4 一键验收：77 项断言，含上传与解析状态、哈希复用、历史措施沉淀、检索与原文回溯、SSE 事件与 `TL-5031` 降级、私有文档零命中、重解析快照；未配 `ARK_API_KEY` 时 M4-03 自动跳过 |
| [scripts/e2e-browser.mjs](scripts/e2e-browser.mjs) | 浏览器端到端验收：55 项断言，跑完成地图四视图、详情九条件与「去处理」跳转、知识库上传检索问答与沉淀、执行者视角；需前后端已启动与 playwright-core |
| [scripts/acceptance-m5.sh](scripts/acceptance-m5.sh) | M5 一键验收：TC-M5-01~09，66 项断言，含会话归属/归档、四类工具与数据范围裁剪、引用回溯、评价证据与周期唯一、评价与统计同源、五类建议与采纳建单/改期、采纳审计与降级标记；未配 `ARK_API_KEY` 时模型可用路径自动跳过 |
| [scripts/e2e-m5.mjs](scripts/e2e-m5.mjs) | M5 浏览器端到端：31 项断言，走会话建/问/归档、工具 chips 与入参、引用回溯、评价证据与申诉必填、建议详情、三种角色的导航与越权裁剪 |
| [deploy/ragflow/README.md](deploy/ragflow/README.md) | RAGFlow 一体化部署：官方 v0.17.0 编排、端口与数据卷、建密钥与建知识库步骤、排障 |
| [scripts/ragflow-up.sh](scripts/ragflow-up.sh) | 一条命令起 RAGFlow 全套（含 ES/MySQL/MinIO/Redis），等 API 就绪、建知识库并写好 tl-api 的启动环境 |
| [docs/ADR-001-RAG与Agent选型.md](docs/ADR-001-RAG与Agent选型.md) | 已接受的选型 v1.0：RAGFlow 作检索底座、AgentScope 串流程、不引 Dify、单库加标签分区；含本地源码实证、适配器分层、六条代价、回退路径与判定阈值、五步验证 |
| [config/application-ai.yml](config/application-ai.yml) | 模型与 RAGFlow 配置示例，密钥从环境变量注入 |
| [config/.env.example](config/.env.example) | 环境变量键名，真实值写 `config/.env`，不进版本库 |

## 从日报到工单的字段对应

原表 10 列即一条工单的完整生命周期：

```
工作内容 = 工单标题            负责人   = 负责人（督办与验收）
责任人   = 主责执行人（唯一）   参与人 = 协办（不承担验收与逾期）
遇到的问题 = 问题项 1..N        解决方案 = 措施项 1..N（须带执行人与期限）
汇报结果 = 追踪记录的结果       备注数据 = 指标（计划与完成、不良、能耗）
```

## 已确认的取舍

| 编号 | 结论 |
|---|---|
| Q1 | 一件事一份工单，多天进展写成追踪记录 |
| Q2 | 主责只有一个；多人格与群体格进待认领，工单停在草稿 |
| Q3 | 系统内会话平台，不接外部 IM 的会话存档 |
| Q4 | 人员评价只对管理者显示 |
| Q5 | 导出严格复刻样本版式，可再次导入 |
| Q6 | 响应式 H5，打卡与拍照可用 |
| Q7 | 内网单机跑 tl-api、MySQL 与 RAGFlow；模型走火山引擎 Ark 公网接口 |
| Q8 | 知识库单库加部门与可见范围标签 |
| Q9 | 只做验收与改期理由，不引工作流引擎 |
| Q10 | 花名册导入加手工维护加别名表 |
| Q11 | 试点为生产部与质量部，三到五名用户，两周 |
| Q12 | 模型取 A+B：问答、知识检索、工单与统计只读查询；评价与五类建议先做，只进待确认队列，建单起草推迟 |
| RAG 底座 | RAGFlow 承担解析与检索，AgentScope 串流程，先不引 Dify |
| Q13 | 完成条件取 A：九条条件与权重一次上线，M3 落地；M1 先提示基础缺口 |
| Q14 | 缺口提醒取 A：滞留三日提醒承担者、七日提醒负责人，阈值在 `sys_config` |
| Q15 | 产品名保持 `TraceLink` |

细节与代价记在 `docs/DESIGN.md` 1.6 与 `docs/ADR-001-RAG与Agent选型.md`。

## 完成地图

每张工单的"完成"拆成九条可判定条件（已接单、期限确定、问题关闭、措施执行、结果留痕、证据齐备、阻碍解除、指标达成、验收通过），条件状态从既有表派生，不新增业务状态。三种视图：工单详情右栏的单工单缺口、部门缺口矩阵、同一事项的达成顺序。日快照与两级提醒在凌晨跑，详情页读实时算式。设计见 `docs/DESIGN.md` 4.10。

## 本地启动

前置：JDK 21、Docker Desktop、Node 18 以上。本机 JDK 21 装在 `/Users/zxc/Library/Java/JavaVirtualMachines/jdk-21.0.13+9/Contents/Home`，下面的命令用它当 `JAVA_HOME`。

```
# 1 起库：MySQL 8.0.40，库名 tracelink，root 口令 tracelink
docker compose up -d
docker compose ps            # 等 STATUS 显示 healthy，首次拉镜像约一分钟

# 2 构建后端（Flyway 在应用启动时自动跑 V1–V9 迁移）
export JAVA_HOME=/Users/zxc/Library/Java/JavaVirtualMachines/jdk-21.0.13+9/Contents/Home
mvn -DskipTests package
java -jar tl-api/target/tracelink.jar

# 3 起前端（开发模式，/api 代理到 8090）
cd web && npm install && npm run dev     # 浏览器打开 http://localhost:5180

# 4 打整包：前端产物会被 tl-web 复制进静态目录，一个 jar 同时提供接口与页面
cd web && npm run build && cd .. && mvn -DskipTests package
```

默认账号 `admin` / `admin123`（张厚生，系统管理员）；演示账号 `xuwei`（许伟，生产部负责人）与 `wangxb`（王先兵，质量部执行者）同口令，用来验证负责人与执行者的数据范围差异。种子数据含总公司、生产部、质量部与三名人员。服务端口取 `8090`（可用 `SERVER_PORT` 改），避开本机已有的 8080 与 5173 占用。只验证接口时用：

```
curl -s localhost:8090/api/v1/health
curl -s -X POST localhost:8090/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'
curl -s localhost:8090/api/v1/depts/tree -H "Authorization: Bearer <上一步返回的 token>"
```

数据库口令、端口与库名可用环境变量覆盖：`MYSQL_ROOT_PASSWORD`、`DB_PORT`、`DB_NAME`。模型与 RAGFlow 的密钥写 `config/.env`，不进版本库。

本机 Docker Desktop 出现过虚拟机反复断电的情况（`FATAL: running services: running fs: injecting event blocked for 60s`，随后整机 poweroff），属于文件共享层的故障，与项目无关。先用本机 MySQL 走通联调，命令如下；Docker Desktop 修好后再切回 compose，两者端口与库名一致，应用侧不用改。

```
HOMEBREW_NO_AUTO_UPDATE=1 brew install mysql@8.0
brew services start mysql@8.0
mysql -uroot -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'tracelink'; \
  CREATE DATABASE tracelink CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

### 知识库底座 RAGFlow（可选，一条命令）

不装 RAGFlow 时系统按本地解析 + 全文降级运行，功能可用；要走向量检索与重排，再起这套：

```bash
bash scripts/ragflow-up.sh          # 起 RAGFlow + ES + MySQL + MinIO + Redis，并建知识库
bash scripts/ragflow-up.sh status   # 看容器与 API 探测
bash scripts/ragflow-up.sh stop     # 停容器，数据卷保留
```

首次启动拉 GB 级镜像，官方建议 4 核 / 16 GB 内存 / 50 GB 磁盘。脚本在 `RAGFLOW_API_KEY` 未设置时只起服务，按提示到 `http://localhost:10080` 建密钥后重跑即可；成功后它把 `RAGFLOW_ENABLED`、`RAGFLOW_BASE_URL`、`RAGFLOW_API_KEY`、`RAGFLOW_DATASET_ID` 写进 `deploy/ragflow/.env.tracelink`（已 gitignore），`source` 后重启后端，知识库页的检索来源会从「本地降级」变成 RAGFlow。编排来源、端口、数据卷与排障见 [deploy/ragflow/README.md](deploy/ragflow/README.md)。

## 里程碑进度

M0 骨架、M1 工单与追踪、M2 导入导出与统计、M3 完成地图、M4 知识库与带引用问答、M5 会话与评价建议均已可运行。

**M0** 有登录、部门、职位、人员四个模块的增删改查。

**M1** 有工单建单与派工、八条规则（R1–R8）、追踪记录与时间线、验收与退回、改期与催办、逾期池与阻碍看板、问题项与措施项关闭、审计留痕，接口前缀 `/api/v1/tasks`，页面为「工单中心」与工单详情。

M1 的本机自检走法：用 `admin` 建一张生产部工单（负责人张厚生、主责许伟），换 `xuwei` 登录写进展与结果、提交验收，再用 `admin` 退回一次、`xuwei` 再提交、`admin` 通过；随后可看逾期池、阻碍看板与详情时间线。错误路径也已覆盖：跨部门读取返回 `TL-4032`、同部门同标题重复建单返回 `TL-4093`、进度回退 `TL-4222`、退回未填理由 `TL-4224`。

**M2** 有三条链路，页面分别是「导入导出」与「统计看板」。

| 链路 | 接口 | 说明 |
|---|---|---|
| 导入 | `POST /api/v1/imports`、`GET /{id}/preview`、`PUT /{id}/rows/{rowId}`、`POST /{id}/confirm`、`DELETE /{id}`（撤回）、`GET /{id}/report.xlsx`（校验报告）、`GET /api/v1/reports`（日报页） | 上传后惰性解析，预览返回双识别率与逐行动作（CREATE/MERGE/SKIP），行可在弹窗里改标题、主责、期限与结果/问题文本，确认后逐行事务入库 |
| 导出 | `POST /api/v1/exports`、`GET /{id}/download` | 五个模板：DAILY 日报回填（区间逐日一页、版式复刻源文件）、WEEKLY 周报、LEDGER 工单台账、PERF 绩效数据、CHART 统计图表 |
| 统计 | `GET /api/v1/stats/overview`、`/trend`、`/process`、`/person` | 七个 KPI、趋势、状态分布、部门完成率、逾期分布与前十、复发榜、类型前十、部门周热力、计划与完成对比、人员散点/雷达/漏斗，共 12 类图表，前端用自研 SVG 组件渲染 |

幂等分两层：同一文件重复上传按 `import_batch.file_hash` 直接返回原批次；行按「业务日期 + 工作内容 + 主责人」的指纹跳过，不含页名与行号，所以导出的日报换个版式再导入不会重复建单。真实样本（83 MB、10 页）的实测结果：首次导入明细行 61 行、行识别率 95.1%、人名匹配率 100%（门槛 95% 与 92%），建单 57 张、失败 0 行；把导出的七天日报回导，预览 `CREATE 0 / MERGE 0 / SKIP 57`，确认后新增 0 张、工单总数不变。

**M3** 页面为「完成地图」，与工单详情右栏联动。

| 视图 | 接口 | 说明 |
|---|---|---|
| 单工单完成地图 | `GET /api/v1/tasks/{id}/completion-map` | 实时算式，九条条件带状态、承担者、滞留天数、`actionUrl` 与提示；详情右栏「去处理」按动作能力跳弹窗或滚动到问题项、措施项 |
| 部门缺口矩阵 | `GET /api/v1/completion-map/matrix` | 行是在办工单、列是 G1–G9，格子三色带滞留天数；逾期行置顶，点表头按列排序 |
| 缺口排行 | `GET /api/v1/completion-map/gaps` | 按条件与承担者聚合，`totalTodo` 与矩阵列合计一致，附滞留天数前二十 |
| 事项完成地图 | `GET /api/v1/completion-map/matter/{taskId}` | 沿父子与复发链按时间轴排同一事项的工单，反复停下的条件高亮 |
| 快照与提醒 | `POST /api/v1/completion-map/snapshot`、`GET /api/v1/notifications`、`POST /notifications/{id}/read` | 当日快照可手动重算；三日提醒承担者、七日提醒负责人，只发新增与跨阈值，按收件人幂等 |

九条条件与权重在 `gate_rule`（4 种类型 15 行，每类型合计一百），改库即时生效；矩阵、排行与事项视图读 `task_gate_snapshot` 日快照，快照失败时矩阵返回上一日并置 `stale`，页面给出数据截止时间与「立即重算」。`scripts/acceptance-m3.sh` 108 项断言覆盖九条逐条、权重即时生效、退回后 G9、矩阵与聚合一致、快照陈旧、两级提醒幂等、180 天保留裁剪与千单压测（1050 张约 1.0–1.2 秒）。浏览器侧另有 `node scripts/e2e-browser.mjs`，55 项断言把四视图、详情九条件与「去处理」跳转、知识库与执行者只读视角走一遍，截图落在 `/tmp/tracelink-e2e`。

**M4** 页面为「知识库」，接口前缀 `/api/v1/kb`，迁移脚本 `V9__knowledge_base.sql`。

| 能力 | 接口 | 说明 |
|---|---|---|
| 文档入库 | `POST /api/v1/kb/documents`、`GET /{id}/sync`、`POST /{id}/sync` | multipart 上传（PDF/DOCX/MD/TXT），按 `file_hash` 复用原文档；`PENDING → PARSING → READY/FAILED` 轮询，失败可单篇重试；空文档固定报「未提取到文本」 |
| 文档列表 | `GET /api/v1/kb/documents` | 只列可见文档，按知识域、部门、状态与标题关键字过滤 |
| 检索 | `POST /api/v1/kb/search` | 先按数据范围算出可见文档，再召回切片；未接 RAGFlow 时 `retriever=LOCAL`、`degraded=true`，页面提示降级 |
| 带引用问答 | `POST /api/v1/kb/ask` | SSE 顺序 `retrieved → delta… → citations → done`；无 `ARK_API_KEY` 时在 `retrieved` 后发 `error TL-5031`，页面仍列召回切片 |
| 历史沉淀 | `POST /api/v1/kb/documents/history` | 取最近一年 `DONE` 工单，把问题、措施、验收结果拼成 `HISTORY` 文档，`source_ref` 记工单号，按内容哈希去重 |
| 切片原文 | `GET /api/v1/kb/chunks/{id}` | 引用与检索命中点击回溯源文；重解析后的旧片标「旧快照」，历史引用不变死链 |

本机未装 RAGFlow 时按降级口径运行：解析走本地解析器（Markdown/TXT 按标题与空行分节，500 字上下、15% 重叠），检索走 MySQL `ft_chunk` ngram 全文并 LIKE 兜底，切片快照、权限复核与引用回溯都在本地成立。`scripts/acceptance-m4.sh` 77 项断言 0 失败，`M4-03` 真实模型生成答案与引用因缺 `ARK_API_KEY` 自动跳过，配好密钥后同一脚本会自动走 `delta`、`citations` 与 `done` 分支。

**M5** 页面为「会话助手」与「复盘与建议」，迁移脚本 `V10__chat_eval_suggestion.sql`、`V11__ai_suggestion_accept_result_width.sql`。

| 能力 | 接口 | 说明 |
|---|---|---|
| 会话 | `POST /api/v1/chat/sessions`、`GET /sessions`、`POST /sessions/{id}/archive`、`GET /sessions/{id}/messages` | 会话归属人唯一，跨用户读取 `TL-4032`；归档后提问 `TL-4091`；列表只回本人会话 |
| 流式问答 | `POST /api/v1/chat/sessions/{id}/messages`（SSE） | 事件顺序 `retrieval → tool_call… → delta… → citations → hint → done`；工具入参带结果条数与耗时；模型不可用或失败时降级规则回答并 `done.degraded=true, model=RULE`；`Idempotency-Key` 防重复提交 |
| 四类只读工具 | `task_query`、`stats_query`、`employee_query`、`knowledge_search` | 全部按调用人 `TaskScope` 取数；执行者越权部门被裁剪并在入参标 `clamped=true`，统计只回本人聚合 |
| 评价 | `POST /api/v1/evaluations/run`、`GET /evaluations`、`GET /evaluations/{id}`、`POST /evaluations/{id}/appeal` | 人员六维（交付/留痕/响应/解决/协办/规范）、任务四维（重要/复杂/健康/质量），权重读 `sys_config`；周期唯一，重复触发只返回首次结果；人员口径直接复用 `stats/person`；申诉理由写入 `evidence.appealReason` 并留 `EVAL_APPEAL` 审计 |
| 建议 | `POST /api/v1/ai/suggestions/run`、`GET /ai/suggestions`、`GET /ai/suggestions/{id}`、`POST /ai/suggestions/{id}/accept` | 五类 DISPATCH/ACTION/DUE_DATE/ESCALATE/POLICY_GAP；采纳由应用服务执行确定性动作（建单走 `TSK-1`、改期走改期接口、升级走催办），结果写 `accept_result` 与 `SUGGESTION_ACCEPT` 审计；ACTION 草稿在来源单负责人与执行人相同时改选部门内负载最低的人，避免生成不可执行的建议 |

`scripts/acceptance-m5.sh` 连续两轮 66 通过 / 0 失败 / 1 跳过（模型可用路径待密钥），`node scripts/e2e-m5.mjs` 31/31 通过。会话侧默认走规则降级：未配置 `ARK_API_KEY` 时以工具结果拼装回答并在页面标注「规则回答（降级）」，配置后自动切到模型流式回答；引用与工具调用在两种模式下都成立。

