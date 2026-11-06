# RAGFlow 一体化部署（知识库底座）

本目录是 RAGFlow 的编排定义，取自官方 `infiniflow/ragflow` v0.17.0 的 `docker/`，只改了两处：

| 文件 | 与官方差异 | 原因 |
|---|---|---|
| `docker-compose.yml` | 端口改为 `${SVR_HTTP_PORT}:9380` 与 `${WEB_PORT}:80`，去掉 443 映射 | 避免与本机已占用的 80/443 冲突；本系统只调 API，Web 仅用于建密钥 |
| `.env.example` | 端口、镜像、时区与 MACOS 的默认值 | 本机 macOS、RAGFlow MySQL 用 5455 避开本系统 3306 |
| `nginx/ragflow.conf` | 仅加一行注释 | 说明自带前端不对外暴露 |
| 其余 | 原样 | 便于对照官方升级 |

## 一条命令起全套

```bash
bash scripts/ragflow-up.sh          # 起服务 → 等 API 就绪 → 建知识库 → 写启动环境
bash scripts/ragflow-up.sh status   # 看容器与 API 探测
bash scripts/ragflow-up.sh stop     # 停容器，保留数据卷
```

首次启动要拉 GB 级镜像，官方建议 4 核 / 16 GB 内存 / 50 GB 磁盘。脚本在 `RAGFLOW_API_KEY` 未设置时只起服务，并把取密钥步骤打印出来：

1. 打开 `http://localhost:10080` 注册登录，在用户设置页创建 API Key（入口随版本略有差异）
2. `export RAGFLOW_API_KEY=<密钥>` 后重跑脚本
3. 脚本创建知识库 `tracelink`（已存在则复用），把 `RAGFLOW_ENABLED`、`RAGFLOW_BASE_URL`、`RAGFLOW_API_KEY`、`RAGFLOW_DATASET_ID` 写入 `.env.tracelink`
4. `source deploy/ragflow/.env.tracelink` 后重启 `tl-api`，知识库页的检索来源从「本地降级」变成 RAGFlow

## 端口与数据

| 端口 | 用途 |
|---|---|
| 9380 | RAGFlow API（`RAGFLOW_BASE_URL` 默认指向它） |
| 10080 | RAGFlow 自带 Web（仅首次建密钥与运维用） |
| 5455 | RAGFlow 自己的 MySQL（本系统 MySQL 仍是 3306） |
| 1200 | Elasticsearch |
| 9000 / 9001 | MinIO API / 控制台（宿主端口被占时用 `.env` 的 `MINIO_PORT` / `MINIO_CONSOLE_PORT` 改到 9010 / 9011） |
| 6379 | Redis（valkey） |

数据卷为 `mysql_data`、`esdata01`、`minio_data`、`redis_data`，`stop` 不会删除。备份按 RAGFlow 官方文档走卷级别备份。

## 与本系统的接缝

代码层早已隔离，开启只改环境变量，业务代码不动：

- 端口在 `tl-domain/.../kb/remote/KbRemotePort.java` 与 `retriever/KnowledgeRetriever.java`
- 适配器在 `tl-infra/.../kb/ragflow/RagFlowClient.java`
- 开关在 `tl-infra/.../kb/config/KbRetrieverConfig.java`：`RAGFLOW_ENABLED=true` 且密钥齐全走向量检索，远端失败按 `degrade-on-error` 回落本地全文
- 配置键在 `tl-api/src/main/resources/application.yml` 的 `tracelink.ai.ragflow`

RAGFlow 不可用或未部署时，系统按本地解析 + ngram 全文降级运行，验收口径见 `docs/TEST-CASES.md` 第 9 章。

## 向量模型（必需，最容易漏）

RAGFlow 解析文档前必须绑定 embedding 模型，否则每个文档都会 `FAILED`、`progress_msg` 里写着
`Fail to bind embedding model: Type of embedding model is not set.`，本系统侧表现为文档 `FAILED`、`chunkCnt=0`。
`slim` 镜像不含内置向量模型，配法二选一：

- **宿主 Ollama（本机采用）**：`ollama pull bge-m3`（1024 维），进 `http://localhost:10080` → 用户设置 → 模型提供商 →
  Ollama → `base_url` 填 `http://host.docker.internal:11434`（本编排已为容器加 `host.docker.internal:host-gateway`）→
  添加模型 `bge-m3` → 设为默认
- **云端向量**：在同一个页面按厂商填密钥，例如火山的 `VolcEngine`（`ark_api_key` + `endpoint_id`）

已建好的知识库不会自动换模型，要把它的 `embd_id` 改成 `bge-m3@Ollama`（`@` 后面是模型厂商）；新建的知识库直接继承租户默认。

## 排障

- `Docker 守护进程未运行`：先启 Docker Desktop。本机曾出现反复断电的 Docker Desktop 崩溃，恢复手段是退出 Docker 后 `rm -f ~/Library/Containers/com.docker.docker/Data/engine.lock` 再启动
- `mysql` 健康检查一直不通过：确认 5455 未被占用；改 `.env` 的 `MYSQL_PORT` 后重跑脚本
- `ragflow` 容器反复重启：先看 `docker compose logs ragflow`，常见是 ES 内存不足，下调 `.env` 的 `MEM_LIMIT`
- 容器被 OOM 杀掉（`OOMKilled`、日志里 python 的 `anon-rss` 逼近 5 GB）：把 Docker Desktop 虚拟机内存调到 16 GB（`~/Library/Group Containers/group.com.docker/settings-store.json` 的 `MemoryMiB`）
- `docker compose up -d` 时 daemon 对每条 `/v1.48/images/create` 返 500（连 `/_ping` 也 500）：并发拉取把虚拟机压挂了，改成先 `docker pull` 逐个拉完再 `up -d`
- `quay.io/minio` 拉不动或镜像站 `denied`：按 `.env.example` 的注释换成 `bitnami/minio`（`MINIO_IMAGE` / `MINIO_COMMAND` / `MINIO_DATA_DIR`）
- API 探测返回 401：服务已起但没有有效 API Key，属正常中间态
