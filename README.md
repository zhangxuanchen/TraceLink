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
| [docs/TEST-CASES.md](docs/TEST-CASES.md) | 验收用例 M0–M5：每条验收条件对应到可执行的 `TC-Mx-nn`，含前置、步骤、断言口径与执行记录；M0–M5 已跑通，含真实模型路径（M4-03 与 TC-M5-09） |
| [scripts/acceptance-m0-m2.sh](scripts/acceptance-m0-m2.sh) | M0–M2 一键验收：76 项断言，含建单到验收一次退回、错误码、导入导出回导零重复 |
| [scripts/acceptance-m3.sh](scripts/acceptance-m3.sh) | M3 一键验收：108 项断言，含九条条件逐条、权重即时生效、矩阵与聚合一致、快照陈旧、两级提醒幂等、保留裁剪与千单压测 |
| [scripts/acceptance-m4.sh](scripts/acceptance-m4.sh) | M4 一键验收：77–79 项断言，含上传与解析状态、哈希复用、历史措施沉淀、检索与原文回溯、SSE 事件与 `TL-5031` 降级、私有文档零命中、重解析快照；未配 `ARK_API_KEY` 时 M4-03 自动跳过，配好后 79 项全过 |
| [scripts/e2e-browser.mjs](scripts/e2e-browser.mjs) | 浏览器端到端验收：53 项断言，跑重点发现里的完成地图四视图、复盘建议视图、详情九条件与「去处理」跳转、知识库上传与文档库（含分页）、执行者视角；需前后端已启动与 playwright-core |
| [scripts/e2e-pages.mjs](scripts/e2e-pages.mjs) | 页面地图补齐验收：77 项断言，走工单中心（页头整排已删、顶部不再有口径 KPI 卡、逾期快捷条可筛列表、列表默认有数据、导出控件并进筛选条且不再单独占卡、「新建工单」紧挨「开始导出」、旧链接 `/desk`、`/reports`、`/tasks/board` 都回跳）、重点发现、人员管理（含角色勾选）、模型配置（AK 与模型 ID 保存即生效、旧设置链接回跳），覆盖侧栏三组分组、统计看板三张结论卡（无底部链接，时效/质量按部门并可下钻）、异步导出从排队到下载、模型配置写入与掩码回显、角色改动落库与越权拦截 |
| [scripts/acceptance-m5.sh](scripts/acceptance-m5.sh) | M5 一键验收：TC-M5-01~09，66 项断言，含会话归属/归档、五类工具与数据范围裁剪、引用回溯、评价证据与周期唯一、评价与统计同源、五类建议与采纳建单/改期、采纳审计与降级标记；配密钥时 0 跳过并断言 `degraded=false`，未配时模型可用路径自动跳过 |
| [scripts/e2e-m5.mjs](scripts/e2e-m5.mjs) | M5 浏览器端到端：32 项断言，走工单助手单会话提问/刷新续用、输入区无范围下拉、工具 chips 与入参、引用回溯、评价证据与申诉必填、建议详情、三种角色的导航与越权裁剪 |
| 浏览器脚本通用参数 | 三个 `e2e-*.mjs` 都认 `BASE` 环境变量：默认 `http://localhost:5180` 打 Vite，`BASE=http://localhost:8090` 则直接验打进 jar 的单端口交付形态 |
| [scripts/ai-restart.sh](scripts/ai-restart.sh) | 环境变量方式配置模型：读 `config/.env` 先做 `chat/completions` 自检（HTTP 码/返回片段/tokens），通过后重启 tl-api 并等健康检查；`--check-only` 只自检不重启。页面已支持在线改 AK 与模型 ID，改完不用重启 |
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

每张工单的"完成"拆成九条可判定条件（已接单、期限确定、问题关闭、措施执行、结果留痕、证据齐备、阻碍解除、指标达成、验收通过），条件状态从既有表派生，不新增业务状态。三种视图：工单详情右栏的单工单缺口、部门缺口矩阵、同一事项的达成顺序。部门矩阵只标未达成条件与滞留天数，已达成的格子留空；没有缺口的工单不进这张问题表。日快照与两级提醒在凌晨跑，详情页读实时算式。设计见 `docs/DESIGN.md` 4.10。

## 页面地图与异步任务

DESIGN 9.2 的页面地图已落到路由，可见角色同时写在路由 `meta.roles` 与侧栏分组里；侧栏分三组「工单（工单中心 / 工单助手 / 统计看板 / 知识库）、组织、系统设置」，统计看板与知识库并入工单组，重点发现不再占侧栏入口、只能从统计看板质量卡或工单详情深链进入；驾驶舱、我的工作台与看板视图都已下线：`/`、`/dashboard`、`/desk`、`/reports`、`/tasks/board` 与登录后落地都进工单中心列表（`#/tasks`），工单中心只有列表一种形态，页头整排（视图切换与新建工单）与顶部七项口径 KPI 卡都已删除，首屏从状态角标开始；日报是工单的集合，也不单开一页，日报回填导出并进工单中心的筛选条（与台账导出、日期区间同排），「新建工单」就排在「开始导出」右边；完成地图与复盘建议合成一页「重点发现」（`/insights`，`?tab=map|review`），旧链接 `/map`、`/stats/review` 会重定向过来；导入向导不进侧栏，工单中心也不再放「上传工作簿」按钮，目前只能深链 `#/import` 进入；角色与账号合进人员管理（`/employees`），旧的 `/settings/roles` 回到人员页：

| 页面 | 路由 | 可见角色 | 首屏内容 | 主要接口 |
|---|---|---|---|---|
| 工单中心 | `/tasks` | 全部 | 页头整排已下线（无标题、无视图切换、顶部也无口径 KPI 卡），自上而下是状态角标（点「逾期」直接筛列表）、筛选条（关键词、状态 / 类型 / 部门三个下拉多选、查询、重置）、右侧并进导出控件：工单台账 / 日报回填两种模板、日期区间、开始导出，「新建工单」紧挨在「开始导出」右边，台账沿用筛选条的条件缩行）与列表（旧链接 `/tasks/board`、`/reports`、`/desk`、`/dashboard` 都会重定向过来） | `GET /tasks`、`POST /exports?async=true` |
| 统计看板 | `/stats` | 管理员、管理者、负责人 | 页首固定三张「重点发现」卡（时效：逾期未结；达成：完成率最低部门，点行筛下方部门表；质量：完成条件积压，点底部按钮跳完成地图下钻），每张写明口径；下面是日期区间筛选与两张表：部门维度与个人维度各算一遍七项率（完成率 / 按时完成率 / 平均完成时长 / 逾期率 / 一次通过率 / 结果留痕率 / 复发率），不再有顶部整体 KPI 卡、趋势与分布图；部门表头带多选部门条件（父部门连下级），个人表按所选部门展示人员并支持姓名 / 部门模糊搜索，两张表每页 10 条；导出控件紧挨查询按钮右侧（部门与个人率表 / 周报 / 人员绩效 / 图表），日期区间直接跟随上方筛选条，不再重复一组日期输入 | `GET /stats/rates`、`GET /tasks/overdue`、`GET /completion-map/gaps`、`POST /exports?async=true` |
| 重点发现 | `/insights` | 不在侧栏；管理员、管理者、负责人可深链进入，执行者可深链看完成地图 | 「完成地图」四视图（部门矩阵/缺口排行/事项视图/我的提醒）与「复盘与建议」（评价、建议队列）；`?tab=map|review` 切换；从统计看板质量卡或工单详情深链进入，三项结论卡已移到统计看板页首，本页不重复汇总 | `GET /completion-map/matrix`、`GET /completion-map/matter/{taskId}`、`GET/POST /evaluations`、`GET /suggestions` |
| 导入向导 | `/import` | 管理员、管理者、负责人 | 三步向导（上传 → 预览校正 → 确认入库）与批次历史；不在侧栏，工单中心也不再放入口，目前只能深链进入 | `POST /imports`、`GET /imports/{id}/preview`、`POST /imports/{id}/confirm` |
| 人员管理 | `/employees` | 全部（角色矩阵只对管理员与管理者显示，改角色仅系统管理员） | 花名册增删改查、账号与角色（四种角色勾选）、角色改动写审计；`/settings/roles` 旧链接回到这里 | `GET /employees`、`GET /accounts`、`PUT /accounts/{employeeId}/roles` |
| 模型配置 | `/settings/model` | 管理员、管理者（仅管理员可改） | 火山方舟接口地址、模型 ID 或推理接入点、API Key（AK）；密钥只回显末四位，保存后下一轮生成即生效；用已保存配置做一次 8 token 的连通性自检 | `GET/PUT /ai/config`、`POST /ai/config/test` |

统一异步任务表 `job_task`（迁移 `V12__job_task_and_settings.sql`）：耗时操作先落一条任务记录再排队，接口立即返回任务 ID，页面按状态轮询，关掉浏览器后重新打开仍能在任务列表看到结果。

- 类型 `PARSE_IMPORT / EXPORT / EVAL / GATE_SNAPSHOT / SYNC_KB`，状态 `PENDING → RUNNING → DONE / FAILED`；字段含 `payload`、`progress`、`attempts`、`next_retry_at`、`last_error`、`result_text`、`locked_by`。
- 异步入口只在显式参数时启用：`POST /api/v1/exports?async=true` 走队列；其余接口保持同步返回，但同样落一条任务记录（`runRecorded`），便于事后回溯。
- 任务列表与详情：`GET /api/v1/jobs`、`GET /api/v1/jobs/{id}`；管理员与管理者看全部，其他人只看自己发起的任务。
- 保留与清理：保留天数取 `sys_config.job.retention.days`（默认 30 天），每日 01:20 由 `JobAppService.purgeExpired` 清理已结束的旧任务。

快照与提醒按自然日生成：跨零点后当天快照还没跑（定时在 02:00），矩阵与缺口页会显示「数据截止 …，今日快照还没跑出来」的提示条（接口 `stale=true`），这是预期行为而不是缺陷；本地复跑 `scripts/e2e-browser.mjs` 前先用管理员调 `POST /api/v1/completion-map/snapshot` 重算当日截面。

## 本地启动

前置：JDK 21、Docker Desktop、Node 18 以上。本机 JDK 21 装在 `/Users/zxc/Library/Java/JavaVirtualMachines/jdk-21.0.13+9/Contents/Home`，下面的命令用它当 `JAVA_HOME`。

```
# 1 起库：MySQL 8.0.40，库名 tracelink，root 口令 tracelink
docker compose up -d
docker compose ps            # 等 STATUS 显示 healthy，首次拉镜像约一分钟

# 2 构建后端（Flyway 在应用启动时自动跑 V1–V12 迁移）
export JAVA_HOME=/Users/zxc/Library/Java/JavaVirtualMachines/jdk-21.0.13+9/Contents/Home
mvn -DskipTests package
java -jar tl-api/target/tracelink.jar

# 3 起前端（开发模式，/api 代理到 8090）
cd web && npm install && npm run dev     # 浏览器打开 http://localhost:5180

# 4 打整包：前端产物会被 tl-web 复制进静态目录，一个 jar 同时提供接口与页面
cd web && npm run build && cd .. && mvn -DskipTests package
```

打包后的静态资源带固定缓存策略（`StaticCacheHeaderFilter`）：入口 `index.html` 与 `/` 是 `Cache-Control: no-cache`，每次打开都回源校验；`/assets/**` 是带哈希的产物，回 `public, max-age=31536000, immutable`。换 jar 重启后浏览器会直接拿到新页面，不用手动清缓存；旧标签页仍是旧脚本，`Cmd+Shift+R` 或关掉重开即可。

默认账号 `admin` / `admin123`（张厚生，系统管理员）；演示账号 `xuwei`（许伟，生产部负责人）与 `wangxb`（王先兵，质量部执行者）同口令，用来验证负责人与执行者的数据范围差异。种子数据含总公司、生产部、质量部与三名人员。服务端口取 `8090`（可用 `SERVER_PORT` 改），避开本机已有的 8080 与 5173 占用。只验证接口时用：

```
curl -s localhost:8090/api/v1/health
curl -s -X POST localhost:8090/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'
curl -s localhost:8090/api/v1/depts/tree -H "Authorization: Bearer <上一步返回的 token>"
```

数据库口令、端口与库名可用环境变量覆盖：`MYSQL_ROOT_PASSWORD`、`DB_PORT`、`DB_NAME`。模型与 RAGFlow 的密钥写 `config/.env`，不进版本库。

接模型只要两步：把 `ARK_API_KEY` 与 `ARK_CHAT_MODEL` 填进 `config/.env`（本机实测可用模型为 `doubao-seed-2-0-mini-260428`，接入点 ID 形如 `ep-...` 也可直接填），再跑：

```
bash scripts/ai-restart.sh --check-only   # 只验连通，打印 HTTP 码与 tokens
bash scripts/ai-restart.sh                # 自检通过后带这套环境变量重启后端
```

脚本先做一次 `chat/completions` 自检，通过后才重启，401/403 是密钥错、404 是模型未开通或写错、000 是网络不通。不配密钥时系统按规则降级运行，功能仍可用。Agent 型 Shell 会在命令结束时回收子进程，那种环境要用后台执行方式启动 `java -jar tl-api/target/tracelink.jar`。

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

首次启动拉 GB 级镜像，官方建议 4 核 / 16 GB 内存 / 50 GB 磁盘。脚本在 `RAGFLOW_API_KEY` 未设置时只起服务，按提示到 `http://localhost:10080` 建密钥后重跑即可；成功后它把 `RAGFLOW_ENABLED`、`RAGFLOW_BASE_URL`、`RAGFLOW_API_KEY`、`RAGFLOW_DATASET_ID` 写进 `deploy/ragflow/.env.tracelink`（已 gitignore），`source` 后重启后端，工单助手的知识检索来源会从「本地降级」变成 RAGFlow。编排来源、端口、数据卷与排障见 [deploy/ragflow/README.md](deploy/ragflow/README.md)。

**本机实测（2026-11-02）已真机打通**：容器、dataset、密钥、解析、切片、检索与问答全部走通，`retriever=RAGFLOW`、`degraded=false`。踩过的坑与最终配置如下。

1. **Docker 侧**：Docker Desktop 虚拟机原先只有 7.65 GB，RAGFlow 解析时内存不够被 OOM（`kmsg.log` 里被杀的正是容器内的 python，`anon-rss` 约 4.8 GB）。把 `~/Library/Group Containers/group.com.docker/settings-store.json` 的 `MemoryMiB` 调到 16384、`Cpus` 调到 8（原文件备份为 `settings-store.json.bak.*`），重启后 `docker info` 显示 15.6 GB / 8 核。
2. **镜像**：`docker compose up -d` 会并发拉 5 个镜像，本机 daemon 对并发 `/v1.48/images/create` 直接返 500（连 `/_ping` 也 500），所以要先**逐个顺序** `docker pull` 再 `up -d`。`minio/minio`、`quay.io/minio` 在镜像站被屏蔽或下载不通，因此 `deploy/ragflow/docker-compose-base.yml` 的 minio 服务改成可替换实现：`.env` 里设 `MINIO_IMAGE=bitnami/minio:latest`、`MINIO_COMMAND=server --console-address ":9001" /bitnami/minio/data"`、`MINIO_DATA_DIR=/bitnami/minio/data`（官方镜像默认值不变）；`MINIO_PORT` 改 9010/9011，避开本机 9000 端口。RAGFlow 镜像是 **linux/amd64**，Apple Silicon 上靠模拟运行，拉取走 `docker.m.daocloud.io` 更快，拉完 `docker tag` 回官方名。
3. **embedding 模型（最容易漏的一步）**：RAGFlow 租户没配向量模型时，解析任务会直接失败——`LookupError: Type of embedding model is not set.`，TraceLink 侧表现为文档 `FAILED`、`chunkCnt=0`。本机用宿主已有的 Ollama（`ollama pull bge-m3`，1024 维，与 `ARK_EMBEDDING_DIM` 一致；`ollama serve` 默认监听 11434）：打开 `http://localhost:10080` → 用户设置 → 模型提供商 → Ollama → `base_url` 填 `http://host.docker.internal:11434` → 添加模型 `bge-m3` → 设为默认。**已建的知识库不会自动换模型**，要把它的 `embd_id` 改成 `bge-m3@Ollama`（新建知识库会直接继承租户默认）。`config/.env` 的 `ARK_EMBEDDING_MODEL` 保持为空即可，向量算在 RAGFlow 侧。
4. **断言**：配好之后按单端口交付形态复跑三个浏览器脚本——`e2e-browser` 53 项（含上传→解析就绪→`chunkCnt=2`）、`e2e-pages` 77 项、`e2e-m5` 32 项。执行记录见 [docs/TEST-CASES.md](docs/TEST-CASES.md)。

排障入口：容器起不来先看 `cd deploy/ragflow && docker compose logs ragflow`；解析失败点开文档的 `progress_msg`，`Fail to bind embedding model` 就是上一条没配；检索 0 命中且 `degraded=false` 说明链路已通、只是库里还没有切片。

## 里程碑进度

M0 骨架、M1 工单与追踪、M2 导入导出与统计、M3 完成地图、M4 知识库与带引用问答、M5 会话与评价建议均已可运行。

**M0** 有登录、部门、职位、人员四个模块的增删改查。

**M1** 有工单建单与派工、八条规则（R1–R8）、追踪记录与时间线、验收与退回、改期与催办、逾期池与阻碍看板、问题项与措施项关闭、审计留痕，接口前缀 `/api/v1/tasks`，页面为「工单中心」与工单详情。

M1 的本机自检走法：用 `admin` 建一张生产部工单（负责人张厚生、主责许伟），换 `xuwei` 登录写进展与结果、提交验收，再用 `admin` 退回一次、`xuwei` 再提交、`admin` 通过；随后可看逾期池、阻碍看板与详情时间线。错误路径也已覆盖：跨部门读取返回 `TL-4032`、同部门同标题重复建单返回 `TL-4093`、进度回退 `TL-4222`、退回未填理由 `TL-4224`。

工单中心只有列表一种形态：状态、类型、部门、逾期、受阻、与我相关与关键词这些筛选都在同一条筛选条上，导出控件（台账 / 日报回填两个模板、日期区间、开始导出）并进这条筛选条的右侧，台账按筛选条件缩行，走异步任务并就地回填进度与下载；「新建工单」紧挨在「开始导出」右边。页头（标题、说明、视图切换与「上传工作簿」按钮）已整排下线，导入向导仍在 `#/import`，需要入口时再挂。限制：导出仍只对管理员、管理者、负责人开放，执行者只有列表与「新建工单」。

**M2** 有三条链路，页面分别是「导入向导」「工单中心」与「统计看板」；导入只在导入向导里做（不再有独立的导入导出页），导出按归属分散成动作：`DAILY`（日报回填）与 `LEDGER`（工单台账）在工单中心筛选条右侧的导出控件里，`WEEKLY/PERF/CHART/RATES` 在统计看板，都走异步任务并在发起页回填进度与下载入口。

| 链路 | 接口 | 说明 |
|---|---|---|
| 导入 | `POST /api/v1/imports`、`GET /{id}/preview`、`PUT /{id}/rows/{rowId}`、`POST /{id}/confirm`、`DELETE /{id}`（撤回）、`GET /{id}/report.xlsx`（校验报告）、`GET /api/v1/reports`（日报页数据，导出日报回填与导入定位用） | 上传后惰性解析，预览返回双识别率与逐行动作（CREATE/MERGE/SKIP），行可在弹窗里改标题、主责、期限与结果/问题文本，确认后逐行事务入库 |
| 导出 | `POST /api/v1/exports`、`GET /{id}/download` | 六个模板：DAILY 日报回填（区间逐日一页、版式复刻源文件）、WEEKLY 周报、LEDGER 工单台账、PERF 绩效数据、CHART 统计图表、RATES 部门与个人率表（两张七项率表，口径同统计看板） |
| 统计 | `GET /api/v1/stats/overview`、`/trend`、`/process`、`/person` | 七个 KPI、趋势、状态分布、部门完成率、逾期分布与前十、复发榜、类型前十、部门周热力、计划与完成对比、人员散点/雷达/漏斗，共 12 类图表，前端用自研 SVG 组件渲染 |

幂等分两层：同一文件重复上传按 `import_batch.file_hash` 直接返回原批次；行按「业务日期 + 工作内容 + 主责人」的指纹跳过，不含页名与行号，所以导出的日报换个版式再导入不会重复建单。真实样本（83 MB、10 页）的实测结果：首次导入明细行 61 行、行识别率 95.1%、人名匹配率 100%（门槛 95% 与 92%），建单 57 张、失败 0 行；把导出的七天日报回导，预览 `CREATE 0 / MERGE 0 / SKIP 57`，确认后新增 0 张、工单总数不变。

**M3** 页面为「重点发现」里的「完成地图」视图（`/insights?tab=map`），与工单详情右栏联动。

| 视图 | 接口 | 说明 |
|---|---|---|
| 单工单完成地图 | `GET /api/v1/tasks/{id}/completion-map` | 实时算式，九条条件带状态、承担者、滞留天数、`actionUrl` 与提示；详情右栏「去处理」按动作能力跳弹窗或滚动到问题项、措施项 |
| 部门缺口矩阵 | `GET /api/v1/completion-map/matrix` | 行是在办工单、列是 G1–G9，格子三色带滞留天数；逾期行置顶，点表头按列排序 |
| 缺口排行 | `GET /api/v1/completion-map/gaps` | 按条件与承担者聚合，`totalTodo` 与矩阵列合计一致，附滞留天数前二十 |
| 事项完成地图 | `GET /api/v1/completion-map/matter/{taskId}` | 沿父子与复发链按时间轴排同一事项的工单，反复停下的条件高亮 |
| 快照与提醒 | `POST /api/v1/completion-map/snapshot`、`GET /api/v1/notifications`、`POST /notifications/{id}/read` | 当日快照可手动重算；三日提醒承担者、七日提醒负责人，只发新增与跨阈值，按收件人幂等 |
| 模型配置 | `GET/PUT /api/v1/ai/config`、`POST /api/v1/ai/config/test` | 火山方舟 AK 与模型 ID 写 `sys_config`（迁移 `V13__ai_model_config.sql`），页面只回显末四位；保存后智能层下一轮即读新值，不用重启；自检只发一条 8 token 的 ping |

九条条件与权重在 `gate_rule`（4 种类型 15 行，每类型合计一百），改库即时生效；矩阵、排行与事项视图读 `task_gate_snapshot` 日快照，快照失败时矩阵返回上一日并置 `stale`，页面给出数据截止时间与「立即重算」。`scripts/acceptance-m3.sh` 108 项断言覆盖九条逐条、权重即时生效、退回后 G9、矩阵与聚合一致、快照陈旧、两级提醒幂等、180 天保留裁剪与千单压测（1050 张约 1.0–1.2 秒）。浏览器侧另有 `node scripts/e2e-browser.mjs`，50 项断言把四视图、详情九条件与「去处理」跳转、知识库文档库与执行者只读视角走一遍，截图落在 `/tmp/tracelink-e2e`。

**M4** 页面为「知识库」，接口前缀 `/api/v1/kb`，迁移脚本 `V9__knowledge_base.sql`。

| 能力 | 接口 | 说明 |
|---|---|---|
| 文档入库 | `POST /api/v1/kb/documents`、`GET /{id}/sync`、`POST /{id}/sync` | multipart 上传（PDF/DOCX/MD/TXT），按 `file_hash` 复用原文档；`PENDING → PARSING → READY/FAILED` 轮询，失败可单篇重试；空文档固定报「未提取到文本」 |
| 文档列表 | `GET /api/v1/kb/documents` | 只列可见文档，按知识域、部门、状态与标题关键字过滤 |
| 检索 | `POST /api/v1/kb/search` | 先按数据范围算出可见文档，再召回切片；未接 RAGFlow 时 `retriever=LOCAL`、`degraded=true`，结果供工单助手使用 |
| 带引用问答 | `POST /api/v1/kb/ask` | SSE 顺序 `retrieved → delta… → citations → done`；无 `ARK_API_KEY` 时在 `retrieved` 后发 `error TL-5031`，工单助手会展示召回切片 |
| 历史沉淀 | `POST /api/v1/kb/documents/history` | 取最近一年 `DONE` 工单，把问题、措施、验收结果拼成 `HISTORY` 文档，`source_ref` 记工单号，按内容哈希去重 |
| 切片原文 | `GET /api/v1/kb/chunks/{id}` | 引用与检索命中点击回溯源文；重解析后的旧片标「旧快照」，历史引用不变死链 |

本机未装 RAGFlow 时按降级口径运行：解析走本地解析器（Markdown/TXT 按标题与空行分节，500 字上下、15% 重叠），检索走 MySQL `ft_chunk` ngram 全文并 LIKE 兜底，切片快照、权限复核与引用回溯都在本地成立。`scripts/acceptance-m4.sh` 未配密钥时 77 项 0 失败、`M4-03` 自动跳过；配好 `ARK_API_KEY` 与 `ARK_CHAT_MODEL` 后复跑 79 项 0 失败 0 跳过，同一脚本自动走 `delta`、`citations` 与 `done` 分支。

**M5** 页面为「工单助手」（只答工单相关：进度、期限、逾期、阻碍、验收退回、口径统计与制度依据）与「重点发现」里的「复盘与建议」视图（`/insights?tab=review`），迁移脚本 `V10__chat_eval_suggestion.sql`、`V11__ai_suggestion_accept_result_width.sql`。

| 能力 | 接口 | 说明 |
|---|---|---|
| 会话 | `POST /api/v1/chat/sessions`、`GET /sessions`、`POST /sessions/{id}/archive`、`GET /sessions/{id}/messages` | 会话归属人唯一，跨用户读取 `TL-4032`；归档后提问 `TL-4091`；列表只回本人会话 |
| 流式问答 | `POST /api/v1/chat/sessions/{id}/messages`（SSE） | 事件顺序 `retrieval → tool_call… → delta… → citations → hint → done`；工具入参带结果条数与耗时；模型不可用或失败时降级规则回答并 `done.degraded=true, model=RULE`；`Idempotency-Key` 防重复提交 |
| 五类只读工具 | `task_query`、`stats_query`、`employee_query`、`org_query`、`knowledge_search` | 全部按调用人 `TaskScope` 取数；执行者越权部门被裁剪并在入参标 `clamped=true`，统计只回本人聚合；`org_query` 回部门编制、在职、负责人、上下级与在办负载 |
| 评价 | `POST /api/v1/evaluations/run`、`GET /evaluations`、`GET /evaluations/{id}`、`POST /evaluations/{id}/appeal` | 人员六维（交付/留痕/响应/解决/协办/规范）、任务四维（重要/复杂/健康/质量），权重读 `sys_config`；周期唯一，重复触发只返回首次结果；人员口径直接复用 `stats/person`；申诉理由写入 `evidence.appealReason` 并留 `EVAL_APPEAL` 审计 |
| 建议 | `POST /api/v1/ai/suggestions/run`、`GET /ai/suggestions`、`GET /ai/suggestions/{id}`、`POST /ai/suggestions/{id}/accept` | 五类 DISPATCH/ACTION/DUE_DATE/ESCALATE/POLICY_GAP；采纳由应用服务执行确定性动作（建单走 `TSK-1`、改期走改期接口、升级走催办），结果写 `accept_result` 与 `SUGGESTION_ACCEPT` 审计；ACTION 草稿在来源单负责人与执行人相同时改选部门内负载最低的人，避免生成不可执行的建议 |

`scripts/acceptance-m5.sh` 在降级口径下 66 通过 / 0 失败 / 1 跳过，配好模型密钥后 66 通过 / 0 失败 / 0 跳过（`done.degraded=false`、`model=doubao-seed-2-0-mini-260428`）；`node scripts/e2e-m5.mjs` 31/31 通过。会话侧未配置 `ARK_API_KEY` 时以工具结果拼装回答并在页面标注「规则回答（降级）」，配置后自动切到模型流式回答，回答来源标签变成具体模型名；引用与工具调用在两种模式下都成立。

