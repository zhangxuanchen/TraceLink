# TraceLink · 企业执行追踪与工单管理系统

把散落在 Excel 日报、群聊截图和口头交办里的事，变成有唯一主责、有期限、有过程记录、有验收结果的工单流，并让每张工单都能回答"还差什么"。

- 技术栈：Spring Boot 3.x · AgentScope(Java) · Maven 多模块 · MySQL 8 · Vue3 + ECharts
- 检索底座：RAGFlow（解析、切片、向量、混合检索与重排）；本轮不引入 Dify
- 三类用户：管理者（看全局与收敛）、负责人（派工与验收）、执行者（接单与打卡）
- 视觉基准：`Memory-Observatory/memory-observatory`（浅色单色系、高信息密度、语义色克制）
- 模型服务：火山引擎 Ark（对话在应用侧，向量与重排在 RAGFlow 侧），配置见 `docs/LLM-CONFIG.md`

## 文档

| 文档 | 内容 |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | 设计文档 v0.7：文档分类与已确认决策 → 样本实测结构与设计约束 → 系统架构与选型理由 → 模块设计（含 4.10 完成地图） → 接口契约与错误码 → ER、33 张表结构与逐字段说明、索引与一致性 → 运行与失败处理 → 非功能设计 → 前端视觉、页面一览、通用交互规则、十个关键流程、断点检查表 → M0–M5 → 3 项新问题 |
| [docs/LLM-CONFIG.md](docs/LLM-CONFIG.md) | 火山引擎 Ark 接入：三套凭据的区别、配置项、示例 yml、与 AgentScope 接线、在 RAGFlow 里配置 Ark、连通性自检、轮换与配额 |
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

细节与代价记在 `docs/DESIGN.md` 1.6 与 `docs/ADR-001-RAG与Agent选型.md`。

## 完成地图

每张工单的"完成"拆成九条可判定条件（已接单、期限确定、问题关闭、措施执行、结果留痕、证据齐备、阻碍解除、指标达成、验收通过），条件状态从既有表派生，不新增业务状态。三种视图：工单详情右栏的单工单缺口、部门缺口矩阵、同一事项的达成顺序。日快照与两级提醒在凌晨跑，详情页读实时算式。设计见 `docs/DESIGN.md` 4.10。

## 本地启动

前置：JDK 21、Docker Desktop、Node 18 以上。本机 JDK 21 装在 `/Users/zxc/Library/Java/JavaVirtualMachines/jdk-21.0.13+9/Contents/Home`，下面的命令用它当 `JAVA_HOME`。

```
# 1 起库：MySQL 8.0.40，库名 tracelink，root 口令 tracelink
docker compose up -d
docker compose ps            # 等 STATUS 显示 healthy，首次拉镜像约一分钟

# 2 构建后端（Flyway 在应用启动时自动跑 V1、V2 迁移）
export JAVA_HOME=/Users/zxc/Library/Java/JavaVirtualMachines/jdk-21.0.13+9/Contents/Home
mvn -DskipTests package
java -jar tl-api/target/tracelink.jar

# 3 起前端（开发模式，/api 代理到 8090）
cd web && npm install && npm run dev     # 浏览器打开 http://localhost:5180

# 4 打整包：前端产物会被 tl-web 复制进静态目录，一个 jar 同时提供接口与页面
cd web && npm run build && cd .. && mvn -DskipTests package
```

默认账号 `admin` / `admin123`，种子数据含生产部、质量部与三名人员。服务端口取 `8090`（可用 `SERVER_PORT` 改），避开本机已有的 8080 与 5173 占用。只验证接口时用：

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

## 下一步

M0 骨架已可运行：登录、部门、职位、人员四个模块的增删改查与数据范围读取。接下来按 `docs/DESIGN.md` 第 10 章推进 M1 工单与追踪，M3 完成地图、M4 RAGFlow 与模型接入。仍需回答 `docs/DESIGN.md` 第 11 章的 3 项（Q13 条件集合、Q14 提醒阈值、Q15 产品名），并按 `docs/LLM-CONFIG.md` 第 7 节跑通两段 curl。

