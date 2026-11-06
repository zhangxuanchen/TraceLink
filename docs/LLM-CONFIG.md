# 模型接入配置（火山引擎 Ark）

版本 v0.3　编写日期 2026-11-02　对应 `docs/DESIGN.md` 的 3.5（模型选型）、4.7（知识库）与第 7 章（失败处理）

线上跑在火山引擎方舟（Ark）上的两类模型：对话模型负责问答、抽取、评价与建议，向量模型负责知识切片与检索。两类都走 Ark 的 OpenAI 兼容接口。对话模型在应用进程里由 AgentScope 的 OpenAI 适配器调用；向量与重排模型在 RAGFlow 侧配置，由 RAGFlow 在解析与检索时调用，应用进程不直接调它们。

## 1 三套凭据不是一回事

这一步最容易配错，所以先分开说。

| 凭据 | 形态 | 用在哪 | 是否本系统需要 |
|---|---|---|---|
| `ARK_API_KEY` | 单串密钥，放 `Authorization: Bearer` 头 | 调 Ark 的推理接口；应用侧的对话、RAGFlow 侧的向量与重排都用它 | 必须 |
| `RAGFLOW_API_KEY` | 单串密钥，由 RAGFlow 自己签发 | 调 RAGFlow 的 REST 接口：上传、解析状态、检索 | 必须 |
| `VOLC_ACCESS_KEY` 与 `VOLC_SECRET_KEY` | AK/SK 成对，请求要按 V4 签名计算 | 调火山引擎其它 OpenAPI，例如对象存储 TOS、IAM、资源管理 | 只在附件改走 TOS 时才需要 |

只用 Ark 推理时，第三行不用配。把 AK/SK 填到 `ARK_API_KEY`，或把 Ark 的密钥填到 `RAGFLOW_API_KEY`，是最常见的两种错配，症状都是所有请求返回鉴权失败，而密钥本身没到期。这两把钥匙方向相反：Ark 的钥匙交给 RAGFlow 用，RAGFlow 的钥匙交给我们的应用用。

## 2 拿到接入点与模型标识

在火山引擎方舟控制台开通模型服务，创建推理接入点，得到三类信息：接入点 ID（形如 `ep-...`）、可用的模型标识、调用域名。对话与向量各建一个接入点，分开计费也分开限流。

`model-name` 这一项填接入点 ID 还是模型标识，取决于控制台给出的形态，两种在 Ark 上都出现过，以控制台为准。这一项不论是环境变量还是页面「模型配置」里都能改，换形态不需要动代码。

2026-11-01 在本机实测：只带 `Authorization: Bearer` 的推理密钥、把 `model` 直接写成模型标识也能通（本机验证可用的对话模型是 `doubao-seed-2-0-mini-260428`），不需要接入点 ID。`GET $ARK_BASE_URL/models` 能列出该账号可见的 138 个模型，其中 55 个未下线；未开通的模型会返回 `404 ModelNotOpen`（提示到控制台开通），所以选型时先用第 7 节的 curl 探一次，再把可用模型写进 `ARK_CHAT_MODEL`。

## 3 配置项

| 键 | 含义 | 初值 | 必填 |
|---|---|---|---|
| `trace-link.ai.ark.base-url` | 接口根地址 | `https://ark.cn-beijing.volces.com/api/v3` | 是 |
| `trace-link.ai.ark.api-key` | 推理密钥，从环境变量注入 | — | 是 |
| `trace-link.ai.ark.chat.model` | 对话接入点或模型标识 | — | 是 |
| `trace-link.ai.ark.chat.timeout` | 单次对话超时 | 30 秒 | 否 |
| `trace-link.ai.ark.chat.max-tokens` | 单次生成上限（含推理 token，推理模型给 4096 起） | 4096 | 否 |
| `trace-link.ai.ark.chat.temperature` | 采样温度，抽取类任务要低 | 0.2 | 否 |
| `trace-link.ai.ark.embedding.model` | 向量接入点或模型标识，仅回退路径与 RAGFlow 对齐用 | — | 否 |
| `trace-link.ai.ark.embedding.dim` | 向量维度，必须与 RAGFlow 侧配置一致 | 待探测 | 否 |
| `trace-link.ai.ark.embedding.batch-size` | 单请求批条数 | 16 | 否 |
| `trace-link.ai.ark.embedding.timeout` | 单次向量超时 | 10 秒 | 否 |
| `trace-link.ai.retry.max-attempts` | 含首次的总尝试次数 | 2 | 否 |
| `trace-link.ai.retry.initial-backoff` | 首次重试等待 | 1 秒 | 否 |
| `trace-link.ai.retry.max-backoff` | 退避上限 | 8 秒 | 否 |
| `trace-link.ai.guard.schema-retry` | 结构不合法时的重试次数 | 1 | 否 |
| `trace-link.ai.guard.on-schema-failure` | 仍不合法时的处置 | `RULE_FALLBACK` | 否 |

以上初值与 `docs/DESIGN.md` 第 7 章的超时口径一致，试点期用真实分布校准。

前三项（`base-url`、`api-key`、`chat.model`）有两处落点：环境变量或配置文件（部署时定），以及页面「模型配置」（运行期改）。两处都填时**页面优先**，页面上清空才回落到环境变量。页面写的三条记录在 `sys_config` 里，键名去掉 `trace-link.` 前缀：`ai.ark.base-url`、`ai.ark.api-key`、`ai.ark.chat.model`，分组 `AI`，由 `V13__ai_model_config.sql` 初始化。

## 4 配置方式与示例

### 4.1 页面在线配置（推荐）

页面入口在侧栏「系统设置 → 模型配置」（`#/settings/model`），管理员与管理者可见，只有系统管理员能保存。原来的「模板与参数」「审计日志」两个页面已下线，旧链接会回到这里；后端的 `GET /configs`、`/gate-rules`、`/audit-logs` 接口保留。

| 字段 | 填法 | 校验 |
|---|---|---|
| 接口地址 | 默认 `https://ark.cn-beijing.volces.com/api/v3` | 必须以 `http://` 或 `https://` 开头 |
| 模型 ID | 控制台给的模型标识或推理接入点 ID，例如 `deepseek-v4-1-flash-economy-261017` | 非空 |
| API Key | 从控制台整段复制 | 非空时长度不少于 8；留空表示沿用已保存的密钥 |

两个按钮的区别要分清：

- **保存并生效**（`PUT /api/v1/ai/config`）：写 `sys_config`，智能层每轮经 `AiSettingsPort` 读一次运行期配置，**保存后下一轮调用即生效，不必重启 tl-api**。有改动时写一条 `SYS_CONFIG` 审计，`before/after` 里的密钥只记末四位掩码。
- **用已保存配置自检**（`POST /api/v1/ai/config/test`）：读当前生效配置，打一次最小对话请求（`max_tokens=8`、`stream=false`），返回 `ok`、HTTP 状态码、耗时、模型名与返回片段。`ok=true` 说明密钥、模型 ID 与出网都通。

页面只回显密钥末四位（形如 `****a2da`），明文不出接口；来源标签区分「页面配置 / 环境变量 / 未配置」，据此判断当前生效的是哪一处。2026-11-02 本机实测：`GET /api/v1/ai/config` 返回 `chatModel=deepseek-v4-1-flash-economy-261017`、`apiKeyMasked=****a2da`、`source=页面配置`、`editable=true`；`POST /api/v1/ai/config/test` 返回 `ok=true`、HTTP 200、约 1.0s、tokens 39。

### 4.2 环境变量方式

配置文件在 `config/application-ai.yml`，通过 `spring.profiles.include` 或 `spring.config.import` 引入。数值只给形状，生产以实际配额为准。

```yaml
trace-link:
  ai:
    provider: volcengine-ark
    ark:
      base-url: ${ARK_BASE_URL:https://ark.cn-beijing.volces.com/api/v3}
      api-key: ${ARK_API_KEY}
      chat:
        model: ${ARK_CHAT_MODEL}
        temperature: 0.2
        max-tokens: 4096
        stream: true
      embedding:
        model: ${ARK_EMBEDDING_MODEL}
        dim: ${ARK_EMBEDDING_DIM:1024}
        batch-size: 16
    retry:
      max-attempts: 2
      initial-backoff: 1s
      max-backoff: 8s
    guard:
      schema-retry: 1
      on-schema-failure: RULE_FALLBACK
      record-cost: true
```

环境变量写在 `config/.env.example` 与本地 `.env`，`.env` 不进版本库（`.gitignore` 已覆盖）。

## 5 与 AgentScope 的接线

AgentScope Java 的 OpenAI 适配器直接读 `agentscope.openai.*`，字段名取自本地仓库 `agentscope-openai-spring-boot-starter` 的 `OpenAIProperties`：

| 键 | 填法 |
|---|---|
| `agentscope.model.provider` | `openai` |
| `agentscope.openai.enabled` | `true` |
| `agentscope.openai.api-key` | `${ARK_API_KEY}` |
| `agentscope.openai.base-url` | `${ARK_BASE_URL}` |
| `agentscope.openai.model-name` | `${ARK_CHAT_MODEL}` |
| `agentscope.openai.stream` | `true` |

`base-url` 已经带 `/api/v3`，适配器在其后拼 `/chat/completions`。若实测发现拼接结果与 Ark 的路径不符，用 `endpoint-path` 单独指定，两个键不要同时改。

向量侧没有对应的 starter，回退路径（本地全文检索加自研向量）才需要它，直接构造 `io.agentscope.core.embedding.openai.OpenAITextEmbedding`：

```java
OpenAITextEmbedding embedding = OpenAITextEmbedding.builder()
        .apiKey(arkProperties.getApiKey())
        .modelName(arkProperties.getEmbedding().getModel())
        .dimensions(arkProperties.getEmbedding().getDim())
        .baseUrl(arkProperties.getBaseUrl())
        .executionConfig(ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(10))
                .maxAttempts(2)
                .initialBackoff(Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(8))
                .build())
        .build();
```

方法名取自本地仓库的 `OpenAITextEmbedding.Builder`，向量侧的构造没有 starter 兜底，所以这几行是手写的接线点。

`dimensions` 是请求参数，Ark 侧是否按此截断需要实测 `[To be confirmed]`。若不生效，以返回向量的实际长度为准，把它写进 `embedding_dim` 并保持全局一致。

## 6 RAGFlow 部署与 Ark 配置

### 6.1 一体化部署与知识库初始化

RAGFlow 是独立的 Python 服务栈，不能作为 Java 依赖嵌进 tl-api，所以"融合"落在两处：代码层用适配器隔离（`KbRemotePort` / `RagFlowClient`），部署层用本仓库的编排把两者当成一套系统起。编排取自官方 `infiniflow/ragflow` v0.17.0 的 `docker/`，放在 `deploy/ragflow/`，只改了端口映射（避开本机 80/443）与 `.env` 默认值，其余原样以便对照升级。

```bash
bash scripts/ragflow-up.sh          # 起 RAGFlow + Elasticsearch + MySQL + MinIO + Redis
bash scripts/ragflow-up.sh status   # 容器状态与 /api/v1 探测
bash scripts/ragflow-up.sh stop     # 停容器，数据卷保留
```

脚本做四件事：补 `deploy/ragflow/.env`（首次从 `.env.example` 复制）、按 `--profile` 起全套并等 `http://127.0.0.1:${SVR_HTTP_PORT}/api/v1/datasets` 可访问、用 `RAGFLOW_API_KEY` 建知识库（已存在则复用，名称由 `RAGFLOW_DATASET_NAME` 定，默认 `tracelink`）、把启动参数写进 `deploy/ragflow/.env.tracelink`（600 权限，已 gitignore）。

首次没有密钥时脚本只起服务，并提示去 `http://localhost:10080` 的用户设置页创建 API Key（入口随版本略有差异）。拿到密钥后：

```bash
export RAGFLOW_API_KEY=<密钥>
bash scripts/ragflow-up.sh          # 建知识库并打印 dataset id
source deploy/ragflow/.env.tracelink
# 重启 tl-api 后生效：RAGFLOW_ENABLED=true、RAGFLOW_BASE_URL、RAGFLOW_API_KEY、RAGFLOW_DATASET_ID
```

端口分配如下，均可在 `deploy/ragflow/.env` 改：

| 端口 | 用途 | 与本系统的关系 |
|---|---|---|
| 9380 | RAGFlow API | `RAGFLOW_BASE_URL` 默认指向它 |
| 10080 | RAGFlow 自带 Web | 仅建密钥与运维，不对外暴露 |
| 5455 | RAGFlow 自有 MySQL | 与本系统 MySQL（3306）分开，互不干扰 |
| 1200 / 9000 / 9001 / 6379 | Elasticsearch / MinIO / Redis | 只被 RAGFlow 使用 |

成功接管后，知识库页的检索来源标签从「本地降级」变为 RAGFlow，`retriever=RAGFLOW`；RAGFlow 挂掉或超时则按 `degrade-on-error=LOCAL_FULLTEXT` 回落本地全文，页面仍给出降级提示，不会出现空白结果。资源要求（官方建议 4 核 / 16 GB / 50 GB）、数据卷备份与排障见 `deploy/ragflow/README.md`。

### 6.2 在 RAGFlow 里配置 Ark

解析与检索用的向量、重排模型不经过本系统，要在 RAGFlow 的模型设置里加一次自定义提供商。它的模型设置支持添加 OpenAI 兼容的自定义服务，字段与 Ark 的接口形态一一对应 `[To be confirmed]`，具体位置以界面为准。

| RAGFlow 侧字段 | 填法 | 说明 |
|---|---|---|
| 提供商标识 | 自定义 OpenAI 兼容 | — |
| 接口地址 | `${ARK_BASE_URL}` | 与应用侧同一个值 |
| 密钥 | `${ARK_API_KEY}` | 与应用侧同一把钥匙 |
| 向量模型名 | `${ARK_EMBEDDING_MODEL}` | 与 `trace-link.ai.ark.embedding.model` 一致 |
| 向量维度 | `${ARK_EMBEDDING_DIM}` | 用第 7 节的 curl 探测，与回退路径一致 |
| 对话或重排模型名 | 按支持的用途逐项填写 | 可与对话模型复用同一接入点 |

三样东西必须一致，否则检索结果与本地快照会对不上：向量模型名、向量维度、接口地址。维度不一致时 RAGFlow 会拒绝写入，这类错误在页面上直接可见，比静默写入坏数据好。

配额要合起来看。RAGFlow 侧的向量调用按解析量计费，应用侧的对话调用按提问量计费，两笔支出不在同一个统计里。

## 7 连通性自检

两段 `curl` 足够判断是密钥错、模型名错还是网络不通；本机也可以直接跑 `bash scripts/ai-restart.sh --check-only`，它读 `config/.env` 后做第一段自检并打印 HTTP 码、返回片段与 tokens。最省事的是页面上的「用已保存配置自检」按钮（`POST /api/v1/ai/config/test`），它用当前生效的配置打同一个请求，结果里带 HTTP 码、耗时与返回片段，不用离开页面。第一段验对话：

```bash
# 密钥与模型写在 config/.env（已 gitignore），填好后可直接用脚本自检
bash scripts/ai-restart.sh --check-only

# 或手工调用
curl -sS "$ARK_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $ARK_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"$ARK_CHAT_MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":8}"
```

返回体里有 `choices` 说明对话通。第二段验向量并直接打印维度：

```bash
curl -sS "$ARK_BASE_URL/embeddings" \
  -H "Authorization: Bearer $ARK_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"$ARK_EMBEDDING_MODEL\",\"input\":[\"工单逾期如何升级\"]}" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(len(d["data"][0]["embedding"]))'
```

打印出来的数字就是 `ARK_EMBEDDING_DIM`。它要填两处：RAGFlow 的向量模型配置，与 `trace-link.ai.ark.embedding.dim`（回退路径用）。主链路的向量由 RAGFlow 维护，本系统不再存向量，换模型时在 RAGFlow 侧重建索引，本地只更新 `kb_document.embedding_model` 用于对照。

## 8 超时、重试与降级

三类失败分开处理，与第 7 章一致。超时按可重试处理，退避一次；限流返回 `TL-4291` 并带 `Retry-After`；返回结构不合法重试一次，仍不合法降级为规则结果并标 `degraded`，页面要标明这一轮不是模型判断。

会话链路的降级不一样，因为用户正在等。模型不可用或失败时不出错中断，而是用工具与检索结果拼一条规则回答，在 `done` 事件里标 `degraded=true`、`model=RULE`，页面据此标注「规则回答（降级）」，本轮不算模型判断。这一条写在 4.8 与第 7 章。

本机填写与生效有两条路。页面路径：在「模型配置」填地址、模型 ID 与 API Key，点「保存并生效」，下一轮调用即生效，不重启进程。环境变量路径：把 `ARK_API_KEY`、`ARK_CHAT_MODEL`（可选 `ARK_EMBEDDING_MODEL`、`RAGFLOW_*`）写进 `config/.env`，然后跑 `bash scripts/ai-restart.sh`——先做一次对话自检，通过后带这套环境变量重启 tl-api 并等健康检查。`--check-only` 只自检不重启。注意 Agent 型 Shell 会在命令结束时回收子进程，那种环境要用后台执行方式启动 `java -jar tl-api/target/tracelink.jar`。

## 9 密钥管理与轮换

密钥的规范落点仍是环境变量：运行期从环境变量读，需要落库的部分（例如按部门配置不同接入点时）用 AES-GCM 加密，主密钥 `TL_SECRET_MASTER_KEY` 只放环境变量，不写进任何配置文件与镜像。加密字段的写法在 `docs/DESIGN.md` 8.1 已列。

页面「模型配置」是本机试点阶段的便利入口，它为这一条开了一个已知例外：保存的 AK 以明文写本机库 `sys_config` 的 `ai.ark.api-key`。可接受的边界有三条——库只在本机、`sys_config` 不对普通角色开放、接口与审计日志里的密钥只出现末四位掩码。**上生产前要把这条例外收掉**，二选一：关掉页面保存、只走环境变量；或把该键改成加密列，页面写入前用 `TL_SECRET_MASTER_KEY` 加密，`AiSettingsPort` 读时解密。无论走哪条，页面「来源」标签都会如实显示当前生效的是页面配置还是环境变量。

轮换按四步走：先在控制台建新密钥并同时保留旧密钥，再更新环境变量并滚动重启（页面配置则可直接改，改完点自检确认），然后观察一天日志确认没有鉴权失败，最后停用旧密钥。顺序不能倒，否则切换窗口内的请求全部失败。

## 10 成本与配额

每一轮调用把模型名、提示词版本、输入与输出 token、耗时记进调用记录，会话链路记在 `chat_message`。配额分两层：按人按日限总量，防止单个账号循环提问；按部门按日限总量，防止一个部门把整月额度吃掉。超限返回 `TL-4291`，不静默降级，因为静默降级会让人以为模型答完了。

## 11 待确认项

| 项 | 待确认内容 | 影响 |
|---|---|---|
| 对话模型 | 具体接入点 ID 与模型标识 | 配置的 `ARK_CHAT_MODEL`；2026-11-02 本机用 `deepseek-v4-1-flash-economy-261017` 自检通过，页面可直接改 |
| 向量模型 | 具体接入点 ID、实际维度、单请求条数上限 | `ARK_EMBEDDING_MODEL`、`ARK_EMBEDDING_DIM`、RAGFlow 侧配置 |
| RAGFlow 的模型设置 | 自定义 OpenAI 兼容提供商是否支持自定义接口地址与维度 | 第 6 节能否按表中填法配置 |
| 路径拼接 | `base-url` 加 `/chat/completions` 是否与实际一致 | 是否需要 `endpoint-path` |
| 配额 | 每日 token 上限的真实额度 | 第 10 节的初值 |
| 出网方式 | 内网是否需要经代理访问 Ark | 3.6 的部署图是否加代理节点 |

这些都在试点前用控制台与两段 `curl` 补齐，补齐后本文档升版本，不再保留待确认标记。
