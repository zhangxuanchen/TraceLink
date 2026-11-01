#!/usr/bin/env bash
# 用 config/.env 里的模型配置重启后端：先做一次 Ark 连通性自检，通过后再拉起 tl-api。
# 用法：bash scripts/ai-restart.sh [--check-only]
#   --check-only 只做自检，不重启后端
# 前置：已构建出 tl-api/target/tracelink.jar（mvn -q -DskipTests package）；
#       config/.env 里至少填了 ARK_API_KEY 与 ARK_CHAT_MODEL。
# 副作用：重启会中断当前运行中的后端进程；日志写到 /tmp/tracelink-backend.log。
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
ENV_FILE="$ROOT/config/.env"
JAR="$ROOT/tl-api/target/tracelink.jar"
PORT=${SERVER_PORT:-8090}
LOG=/tmp/tracelink-backend.log
CHECK_ONLY=${1:-}

if [ ! -f "$ENV_FILE" ]; then
  echo "缺少 config/.env：先 cp config/.env.example config/.env 再填 ARK_API_KEY 与 ARK_CHAT_MODEL" >&2
  exit 2
fi

# 只导出非空值，避免 .env 里的空行覆盖已有环境变量
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

ARK_API_KEY=$(printf '%s' "${ARK_API_KEY:-}" | tr -d '[:space:]')
ARK_CHAT_MODEL=$(printf '%s' "${ARK_CHAT_MODEL:-}" | tr -d '[:space:]')
ARK_BASE_URL=${ARK_BASE_URL:-https://ark.cn-beijing.volces.com/api/v3}
export ARK_API_KEY ARK_CHAT_MODEL ARK_BASE_URL

if [ -z "$ARK_API_KEY" ] || [ -z "$ARK_CHAT_MODEL" ]; then
  echo "config/.env 里 ARK_API_KEY 或 ARK_CHAT_MODEL 为空，填好再跑本脚本" >&2
  exit 2
fi

echo "== 1/3 Ark 连通性自检（chat/completions，模型 ${ARK_CHAT_MODEL}）"
BODY=$(curl -sS -m 30 -w '\n%{http_code}' "$ARK_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $ARK_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"$ARK_CHAT_MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":8}" 2>&1)
CODE=$(printf '%s' "$BODY" | tail -1)
PAYLOAD=$(printf '%s' "$BODY" | sed '$d')

if [ "$CODE" != '200' ]; then
  echo "✘ 自检失败：HTTP $CODE" >&2
  printf '%s\n' "$PAYLOAD" | head -c 600 >&2
  echo >&2
  echo "排查：401/403 多为密钥错或接入点未授权；404 多为模型名/接入点 ID 写错；000 为网络不通" >&2
  exit 1
fi

printf '%s' "$PAYLOAD" | python3 -c '
import json, sys
d = json.load(sys.stdin)
c = (d.get("choices") or [{}])[0]
txt = (c.get("message") or {}).get("content") or ""
usage = d.get("usage") or {}
print("✔ 对话连通：模型 %s，返回 %r，tokens in/out = %s/%s"
      % (d.get("model"), txt[:40], usage.get("prompt_tokens"), usage.get("completion_tokens")))
' || { echo "✘ 返回体不是预期的 choices 结构：" >&2; printf '%s\n' "$PAYLOAD" | head -c 600 >&2; exit 1; }

if [ "$CHECK_ONLY" = '--check-only' ]; then
  echo "== 自检完成，未重启后端（去掉 --check-only 即重启）"
  exit 0
fi

echo "== 2/3 重启后端（端口 ${PORT}）"
if [ -f "$JAR" ]; then :; else echo "缺少 ${JAR}，先 mvn -q -pl tl-api -am -DskipTests package" >&2; exit 2; fi
PIDS=$(lsof -ti "tcp:$PORT" 2>/dev/null || true)
if [ -n "$PIDS" ]; then
  # shellcheck disable=SC2086
  kill $PIDS 2>/dev/null || true
  for _ in $(seq 1 20); do
    lsof -ti "tcp:$PORT" >/dev/null 2>&1 || break
    sleep 0.5
  done
  echo "   已停止占用 $PORT 的旧进程：$(printf '%s' "$PIDS" | tr '\n' ' ')"
fi

nohup java -jar "$JAR" > "$LOG" 2>&1 &
NEW_PID=$!

echo "== 3/3 等健康检查（最多 60 秒，PID ${NEW_PID}，日志 ${LOG}）"
for i in $(seq 1 60); do
  if curl -s -o /dev/null -m 2 "http://127.0.0.1:$PORT/api/v1/health"; then
    echo "✔ 后端就绪：http://127.0.0.1:$PORT/api/v1/health"
    # Agent 型 Shell 会在命令结束时回收子进程，这里复核一次，避免报「已重启」其实进程已没了
    sleep 2
    if kill -0 "$NEW_PID" 2>/dev/null; then
      echo "   进程存活，下一步：bash scripts/acceptance-m5.sh（TC-M5-09 应走模型可用路径）"
      exit 0
    fi
    echo "⚠ 后端在命令结束时被回收（Agent Shell 会清理本命令的子进程，属预期）" >&2
    echo "   请改用后台执行方式启动，例如在 IDE 里以 run_in_background 执行：" >&2
    echo "   bash -c 'set -a; . config/.env; set +a; exec java -jar tl-api/target/tracelink.jar'" >&2
    exit 3
  fi
  if ! kill -0 "$NEW_PID" 2>/dev/null; then
    echo "✘ 进程已退出，最后 20 行日志：" >&2
    tail -20 "$LOG" >&2
    exit 1
  fi
  sleep 1
done

echo "✘ 60 秒内未就绪，最后 20 行日志：" >&2
tail -20 "$LOG" >&2
exit 1
