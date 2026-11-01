#!/usr/bin/env bash
# RAGFlow 一体化启动：拉起官方编排（RAGFlow + Elasticsearch + MySQL + MinIO + Redis），
# 等 /api/v1 就绪，用 RAGFLOW_API_KEY 建好知识库，并把本系统要用的环境变量写到
# deploy/ragflow/.env.tracelink（已 gitignore），启动 tl-api 前 source 它即可。
# 用法：bash scripts/ragflow-up.sh [up|stop|status]
# 前置：Docker 守护进程运行中；首次启动需拉取镜像（GB 级，约 10 分钟起）。
# 说明：未设置 RAGFLOW_API_KEY 时只起服务并打印取密钥步骤，不建知识库。
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIR="$ROOT/deploy/ragflow"
CMD=${1:-up}

envval() { # 从 deploy/ragflow/.env 取一个键的值，去引号
  grep -E "^$1=" "$DIR/.env" 2>/dev/null | tail -1 | cut -d= -f2- \
    | sed -e "s/^'//" -e "s/'$//" -e 's/^"//' -e 's/"$//'
}

need_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo '未找到 docker 命令，请先安装 Docker Desktop' >&2
    exit 2
  fi
  if ! docker info >/dev/null 2>&1; then
    echo 'Docker 守护进程未运行，先启动 Docker Desktop（macOS 见 README 的排障说明）' >&2
    exit 2
  fi
}
dc() { ( cd "$DIR" && docker compose -f docker-compose.yml "$@" ); }

prepare_env() {
  if [ ! -f "$DIR/.env" ]; then
    cp "$DIR/.env.example" "$DIR/.env"
    echo "已从 .env.example 生成 deploy/ragflow/.env（密码可自行改，不进版本库）"
  fi
}

wait_api() {
  local port code i
  port=$(envval SVR_HTTP_PORT); port=${port:-9380}
  printf '等待 RAGFlow API 就绪（127.0.0.1:%s，最多 5 分钟）' "$port"
  for i in $(seq 1 60); do
    code=$(curl -s -o /dev/null -m 3 -w '%{http_code}' "http://127.0.0.1:$port/api/v1/datasets" 2>/dev/null || true)
    if [ "$code" != '000' ] && [ -n "$code" ]; then
      echo " → HTTP ${code}"
      return 0
    fi
    printf '.'
    sleep 5
  done
  echo
  return 1
}

bootstrap_dataset() {
  local port out ds_id
  port=$(envval SVR_HTTP_PORT); port=${port:-9380}
  out=$(python3 - "$port" "${RAGFLOW_API_KEY}" "${RAGFLOW_DATASET_NAME:-tracelink}" <<'PY'
import json, sys, urllib.error, urllib.request

port, key, name = sys.argv[1], sys.argv[2], sys.argv[3]
base = f"http://127.0.0.1:{port}/api/v1"


def call(path, method="GET", body=None):
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(
        base + path, data=data, method=method,
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raise SystemExit(f"ERROR HTTP {e.code} {e.read().decode()[:200]}")
    except urllib.error.URLError as e:
        raise SystemExit(f"ERROR 连接失败 {e.reason}")


listing = call("/datasets?page=1&page_size=100")
items = listing.get("data") or []
if isinstance(items, dict):
    items = items.get("items") or []
found = next((d.get("id") for d in items if d.get("name") == name), None)
if found:
    print(found)
    raise SystemExit(0)

created = call("/datasets", "POST", {"name": name, "chunk_method": "naive"})
if created.get("code") != 0:
    raise SystemExit("ERROR " + json.dumps(created, ensure_ascii=False)[:200])
print(created["data"]["id"])
PY
) || { echo "建知识库失败：$out" >&2; return 1; }
  ds_id=$(printf '%s\n' "$out" | tail -1)
  if [ -z "$ds_id" ]; then
    echo '建知识库失败：未取到 dataset id' >&2
    return 1
  fi
  port=$(envval SVR_HTTP_PORT); port=${port:-9380}
  {
    echo '# 由 scripts/ragflow-up.sh 生成，source 后再启动 tl-api；密钥不会进版本库'
    echo 'export RAGFLOW_ENABLED=true'
    echo "export RAGFLOW_BASE_URL=http://127.0.0.1:${port}"
    echo "export RAGFLOW_API_KEY='${RAGFLOW_API_KEY}'"
    echo "export RAGFLOW_DATASET_ID='${ds_id}'"
  } > "$DIR/.env.tracelink"
  chmod 600 "$DIR/.env.tracelink"
  echo "知识库 ${RAGFLOW_DATASET_NAME:-tracelink} 已就绪（dataset id: ${ds_id}）"
  echo "已写入 $DIR/.env.tracelink（密钥已隐藏），启动 tl-api 前执行："
  echo "  source deploy/ragflow/.env.tracelink"
  echo '然后重启后端，知识库页的检索来源会从「本地降级」变为 RAGFlow。'
}

case "$CMD" in
  up)
    need_docker
    prepare_env
    echo '首次启动会拉取 RAGFlow、Elasticsearch、MySQL、MinIO、Redis 镜像，请预留时间与磁盘'
    profile=$(envval DOC_ENGINE); profile=${profile:-elasticsearch}
    case "$profile" in *'${'*) profile=elasticsearch ;; esac
    dc --profile "$profile" up -d || { echo '启动失败，查看：cd deploy/ragflow && docker compose logs' >&2; exit 1; }
    if ! wait_api; then
      echo 'RAGFlow API 在 5 分钟内未就绪，查看日志：cd deploy/ragflow && docker compose logs ragflow' >&2
      exit 1
    fi
    if [ -z "${RAGFLOW_API_KEY:-}" ]; then
      web_port=$(envval WEB_PORT); web_port=${web_port:-10080}
      cat <<EOF
RAGFlow 服务已启动，但还没有 API Key，暂不建知识库：
  1. 打开 http://localhost:${web_port} 注册并登录，在用户设置页创建 API Key（入口随版本略有差异）
  2. export RAGFLOW_API_KEY=<刚创建的密钥>
  3. 重跑 bash scripts/ragflow-up.sh（会建好知识库并打印 tl-api 的启动环境）
EOF
      exit 0
    fi
    bootstrap_dataset
    ;;
  stop)
    need_docker
    dc down
    echo '已停止容器，数据卷保留；要连数据一起清掉：cd deploy/ragflow && docker compose down -v'
    ;;
  status)
    need_docker
    dc ps
    port=$(envval SVR_HTTP_PORT); port=${port:-9380}
    code=$(curl -s -o /dev/null -m 3 -w '%{http_code}' "http://127.0.0.1:$port/api/v1/datasets" 2>/dev/null || true)
    echo "API 探测 http://127.0.0.1:${port}/api/v1/datasets → HTTP ${code:-000}（401 表示服务已起但缺密钥）"
    ;;
  *)
    echo '用法：bash scripts/ragflow-up.sh [up|stop|status]' >&2
    exit 2
    ;;
esac
