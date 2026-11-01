#!/usr/bin/env bash
# TraceLink M4 验收入口。对应用例见 docs/TEST-CASES.md 第 9 章 M4-01 ~ M4-07。
# 用法：bash scripts/acceptance-m4.sh
# 前置：后端已启动在 8090、Flyway 已到 V9、MySQL 可用（root/tracelink，库 tracelink）。
# 说明：未配置 RAGFlow 与 ARK_API_KEY 时走本地解析 + 全文降级，M4-03 会标记为待密钥。
set -uo pipefail

B=${B:-http://127.0.0.1:8090/api/v1}
TMP=$(mktemp -d)
PASS=0
FAIL=0
SKIP=0
FAIL_LIST=()
SKIP_LIST=()
SUF=$(date +%H%M%S)
PLAN_END=$(date -v+10d '+%Y-%m-%dT18:00:00' 2>/dev/null || date -d '+10 day' '+%Y-%m-%dT18:00:00')
ARTIFICIAL_PARSE_DOC=''

pass() { PASS=$((PASS + 1)); printf '  \033[32m✔\033[0m %s\n' "$1"; }
fail() { FAIL=$((FAIL + 1)); FAIL_LIST+=("$1 — $2"); printf '  \033[31m✘\033[0m %s：%s\n' "$1" "$2"; }
skip() { SKIP=$((SKIP + 1)); SKIP_LIST+=("$1 — $2"); printf '  \033[33m-\033[0m %s：%s\n' "$1" "$2"; }
section() { printf '\n\033[1m%s\033[0m\n' "$1"; }

if ! command -v mysql >/dev/null 2>&1; then
  echo '需要 mysql 客户端造数与断言（kb_document / kb_chunk / task）' >&2
  exit 2
fi

sql() { mysql -N --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uroot -p'tracelink' tracelink -e "$1" 2>/dev/null; }

cleanup_previous() { # 只清本脚本上一轮留下的【M4验收】文档与工单，种子单 1–5 不动
  local docs list
  docs=$(sql "select id from kb_document
              where title like '【M4验收】%' or title like '历史问题与措施：【M4验收】%'" | paste -sd, -)
  if [ -n "$docs" ]; then
    sql "delete from kb_chunk where doc_id in ($docs);
         delete from audit_log where biz_type='KB' and biz_id in ($docs);
         delete from kb_document where id in ($docs);"
  fi
  list=$(sql "select id from task where title like '【M4验收】%' and id > 5" | paste -sd, -)
  [ -z "$list" ] && return 0
  sql "delete from notification where task_id in ($list);
       delete from task_gate_snapshot where task_id in ($list);
       delete from task_checkin where task_id in ($list);
       delete from task_issue where task_id in ($list);
       delete from task_action where task_id in ($list);
       delete from task_assignee where task_id in ($list);
       delete from audit_log where biz_type='TASK' and biz_id in ($list);
       delete from task where id in ($list);"
}

restore_env() { # 断言过程中被人工置为 PARSING 的文档恢复原状
  if [ -n "$ARTIFICIAL_PARSE_DOC" ]; then
    sql "update kb_document set sync_status='READY' where id=$ARTIFICIAL_PARSE_DOC;"
  fi
}
trap restore_env EXIT

call() { # METHOD PATH TOKEN [BODY] -> HTTP 状态码，响应落到 $TMP/body.json
  local method=$1 path=$2 token=$3 body=${4:-}
  local args=(-s -o "$TMP/body.json" -w '%{http_code}' -X "$method" "$B$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  curl "${args[@]}"
}

call_json() { # METHOD PATH TOKEN JSONFILE
  curl -s -o "$TMP/body.json" -w '%{http_code}' -X "$1" "$B$2" \
    -H "Authorization: Bearer $3" -H 'Content-Type: application/json' -d @"$4"
}

jget() { # 取顶层字段，如 code
  python3 - "$TMP/body.json" "$1" <<'PY'
import json,sys
print(json.load(open(sys.argv[1])).get(sys.argv[2], ""))
PY
}

jexpr() { # 对 data 求值，如 d['total']；布尔统一输出小写，取不到时输出空
  python3 - "$TMP/body.json" "$1" <<'PY'
import json,sys
try:
    d=json.load(open(sys.argv[1])).get('data')
    v=eval(sys.argv[2])
    if isinstance(v, bool):
        print('true' if v else 'false')
    elif v is None:
        print('')
    else:
        print(v)
except Exception:
    print('')
PY
}

expect() {
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "期望 [$2]，实际 [$3]"; fi
}

expect_http() {
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "HTTP 期望 $2，实际 $3"; fi
}

expect_code() {
  local actual
  actual=$(jget code)
  expect "$1" "$2" "$actual"
}

login() {
  curl -s -X POST "$B/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"admin123\"}" \
    | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('token','') if d.get('code')=='0' else '')"
}

upload_doc() { # 文件 标题 部门 可见范围 知识域 -> HTTP 状态码，响应落到 $TMP/body.json
  curl -s -o "$TMP/body.json" -w '%{http_code}' -X POST "$B/kb/documents" \
    -H "Authorization: Bearer $T_ADMIN" \
    -F "file=@$1" -F "title=$2" -F "deptId=$3" -F "visibility=$4" -F "domain=$5"
}

poll_sync() { # 文档ID 期望状态 [轮次]；失败态提前返回 1
  local id=$1 want=$2 rounds=${3:-40} status=''
  local i
  for i in $(seq 1 "$rounds"); do
    sleep 0.6
    call GET "/kb/documents/$id/sync" "$T_ADMIN" >/dev/null
    status=$(jexpr "d['syncStatus']")
    [ "$status" = "$want" ] && return 0
    if [ "$want" != 'FAILED' ] && [ "$status" = 'FAILED' ]; then
      return 1
    fi
  done
  return 1
}

doc_field() { # 文档ID 字段（通过列表接口按本轮后缀取，避免依赖单文档详情）
  call GET "/kb/documents?keyword=$SUF" "$T_ADMIN" >/dev/null
  jexpr "next((x['$2'] for x in d if x['id']==$1), '')"
}

# ---------------------------------------------------------------- 准备
section '准备：登录、健康检查与上一轮数据清理'
cleanup_previous
health=$(curl -s -o /dev/null -w '%{http_code}' "${B%/api/v1}/actuator/health")
expect_http '健康检查返回 200' 200 "$health"
T_ADMIN=$(login admin)
T_EXEC=$(login wangxb)
expect 'admin 取得令牌' 'non-empty' "$([ -n "$T_ADMIN" ] && echo non-empty || echo empty)"
expect 'wangxb 取得令牌' 'non-empty' "$([ -n "$T_EXEC" ] && echo non-empty || echo empty)"
call GET '/employees/options' "$T_ADMIN" >/dev/null
OWNER_ID=$(jexpr "next((e['id'] for e in d if e['name']=='许伟'), '')")
PROD_EXEC_ID=$(jexpr "next((e['id'] for e in d if e['name']=='曹长坤'), '')")
expect '负责人定位到生产部许伟' 'non-empty' "$([ -n "$OWNER_ID" ] && echo non-empty || echo empty)"
expect '生产部执行者定位到曹长坤' 'non-empty' "$([ -n "$PROD_EXEC_ID" ] && echo non-empty || echo empty)"
call GET '/employees/options' "$T_ADMIN" >/dev/null
QA_EXEC_ID=$(jexpr "next((e['id'] for e in d if e['name']=='王先兵'), '')")
expect '质量部执行者定位到王先兵' 'non-empty' "$([ -n "$QA_EXEC_ID" ] && echo non-empty || echo empty)"

POLICY="$TMP/policy-$SUF.md"
EMPTY="$TMP/empty-$SUF.md"
PDF_TXT="$TMP/pdf-$SUF.txt"
PDF="$TMP/pdf-$SUF.pdf"
MARK="KBZQ$(echo "$SUF" | tr '0123456789' 'ABCDEFGHIJ')"

cat > "$POLICY" <<EOF
# 生产部冲压工位毛刺处理制度（${SUF}）

## 一、适用范围

本制度适用于生产部冲压、折弯与去毛刺工位。毛刺超标指单件毛刺高度超过 0.2 毫米，或首件检验出现连续两件超差。班组长发现毛刺超标后应第一时间隔离当批在制品，并在工单中登记问题与措施。

## 二、处置措施

1. 更换磨损或间隙超差的模具，重新对刀并试冲三件。
2. 增加去毛刺工序，使用锉刀与砂带机处理边角，处理后首件三检合格才可批量生产。
3. 对已产出批次按追溯卡范围复检，超差品走返工流程，返工后再次全检并留存影像记录。
4. 班后组织原因分析，把模具保养周期与首件检验要求写入点检表，防止同类问题重复发生。

## 三、记录与验收

处置完成后在工单追踪里写清措施、责任人与完成时间，提交验收时附上首件三检记录与整改前后对比照片。负责人验收通过后关闭工单，并在周例会上复盘。

验收标识：${MARK}
EOF

python3 - "$SUF" "$EMPTY" <<'PY'
import sys
n = int(sys.argv[1][-2:])
open(sys.argv[2], 'w', encoding='utf-8').write(' ' * (200 + n) + '\n\n' + ' ' * (50 + n))
PY

cat > "$PDF_TXT" <<EOF
Burr control policy for stamping line $SUF

Section 1: Isolate the batch when burr height exceeds the limit.
Section 2: Replace worn dies and run first article inspection.
Section 3: Add a deburring step before batch production and keep photos in the ticket.
EOF
PDF_OK=0
if command -v cupsfilter >/dev/null 2>&1; then
  if cupsfilter "$PDF_TXT" > "$PDF" 2>/dev/null && [ -s "$PDF" ]; then
    PDF_OK=1
  fi
fi

# ---------------------------------------------------------------- M4-01
section 'M4-01 文档上传、解析状态流转与哈希去重'
code=$(upload_doc "$POLICY" "【M4验收】$SUF 冲压毛刺处理制度" 2 DEPT POLICY)
expect_http 'TC-M4-01 上传制度 md 成功' 200 "$code"
expect_code 'TC-M4-01 返回成功业务码' 0
DOC_A=$(jexpr "d['id']")
FIRST_STATUS=$(jexpr "d['syncStatus']")
if [ "$FIRST_STATUS" = 'PENDING' ] || [ "$FIRST_STATUS" = 'PARSING' ] || [ "$FIRST_STATUS" = 'READY' ]; then
  pass 'TC-M4-01 上传后立即进入待解析或解析中'
else
  fail 'TC-M4-01 上传后立即进入待解析或解析中' "实际 [$FIRST_STATUS]"
fi
if poll_sync "$DOC_A" READY 40; then
  pass 'TC-M4-01 轮询后状态变为就绪'
else
  fail 'TC-M4-01 轮询后状态变为就绪' "最终状态 $(jexpr "d['syncStatus']")"
fi
call GET "/kb/documents/$DOC_A/sync" "$T_ADMIN" >/dev/null
CHUNK_A=$(jexpr "d['chunkCnt']")
expect 'TC-M4-01 就绪后切片数大于 0' 'true' "$([ "${CHUNK_A:-0}" -gt 0 ] && echo true || echo false)"
expect 'TC-M4-01 本地解析走降级检索器' 'LOCAL' "$(jexpr "d['retriever']")"

code=$(upload_doc "$POLICY" "【M4验收】$SUF 冲压毛刺处理制度" 2 DEPT POLICY)
expect_http 'TC-M4-01 同内容重复上传成功' 200 "$code"
DUP_ID=$(jexpr "d['id']")
expect 'TC-M4-01 重复上传按哈希复用原文档' "$DOC_A" "$DUP_ID"
expect 'TC-M4-01 复用文档切片数不变' "$CHUNK_A" "$(jexpr "d['chunkCnt']")"

code=$(upload_doc "$EMPTY" "【M4验收】$SUF 空文档" 2 DEPT POLICY)
expect_http 'TC-M4-01 空白文档上传成功' 200 "$code"
DOC_EMPTY=$(jexpr "d['id']")
if poll_sync "$DOC_EMPTY" FAILED 20; then
  pass 'TC-M4-01 空白文档解析失败'
else
  fail 'TC-M4-01 空白文档解析失败' "最终状态 $(jexpr "d['syncStatus']")"
fi
call GET "/kb/documents/$DOC_EMPTY/sync" "$T_ADMIN" >/dev/null
expect 'TC-M4-01 失败原因说明为空文档' 'true' \
  "$(jexpr "'未提取到文本' in (d['syncMsg'] or '')")"
code=$(call POST "/kb/documents/$DOC_EMPTY/sync" "$T_ADMIN")
expect_http 'TC-M4-01 失败文档可再次触发解析' 200 "$code"
if poll_sync "$DOC_EMPTY" FAILED 20; then
  pass 'TC-M4-01 重试后仍按空文档判失败'
else
  fail 'TC-M4-01 重试后仍按空文档判失败' "最终状态 $(jexpr "d['syncStatus']")"
fi

if [ "$PDF_OK" = '1' ]; then
  code=$(upload_doc "$PDF" "【M4验收】$SUF 毛刺制度 PDF" 2 DEPT POLICY)
  expect_http 'TC-M4-01 上传 PDF 成功' 200 "$code"
  DOC_PDF=$(jexpr "d['id']")
  if poll_sync "$DOC_PDF" READY 40; then
    pass 'TC-M4-01 PDF 解析就绪'
  else
    fail 'TC-M4-01 PDF 解析就绪' "最终状态 $(jexpr "d['syncStatus']")"
  fi
  call GET "/kb/documents/$DOC_PDF/sync" "$T_ADMIN" >/dev/null
  expect 'TC-M4-01 PDF 抽取到切片' 'true' "$([ "$(jexpr "d['chunkCnt']")" -gt 0 ] && echo true || echo false)"
else
  skip 'TC-M4-01 上传 PDF' '本机无 cupsfilter，未生成 PDF 样本'
fi

# ---------------------------------------------------------------- M4-02
section 'M4-02 历史问题与措施沉淀，同题可召回历史措施'
cat > "$TMP/task.json" <<EOF
{"title":"【M4验收】$SUF 冲压工位毛刺超标整改","type":"QUALITY","deptId":2,
 "ownerId":$OWNER_ID,"primaryAssigneeId":$PROD_EXEC_ID,"priority":2,"planEnd":"$PLAN_END",
 "issueList":["冲压工位毛刺超标，模具间隙偏大，首件检验连续两件超差"],
 "actionList":[{"content":"更换磨损模具并增加去毛刺工序，首件三检合格后再批量生产","ownerId":$PROD_EXEC_ID,"dueDate":"$(date '+%Y-%m-%d')"}]}
EOF
code=$(call_json POST '/tasks' "$T_ADMIN" "$TMP/task.json")
expect_http 'TC-M4-02 建历史样本工单成功' 201 "$code"
TASK_ID=$(jexpr "d['taskId']")
TASK_NO=$(jexpr "d['taskNo']")
expect 'TC-M4-02 工单号已生成' 'true' "$([ -n "$TASK_NO" ] && echo true || echo false)"
call POST "/tasks/$TASK_ID/dispatch" "$T_ADMIN" \
  "{\"primaryAssigneeId\":$PROD_EXEC_ID,\"planEnd\":\"$PLAN_END\"}" >/dev/null
expect_code 'TC-M4-02 派工成功' 0
call POST "/tasks/$TASK_ID/checkin" "$T_ADMIN" \
  '{"progress":40,"content":"已隔离当批在制品，等待模具更换","resultType":"PROGRESS"}' >/dev/null
expect_code 'TC-M4-02 过程追踪成功' 0
call POST "/tasks/$TASK_ID/checkin" "$T_ADMIN" \
  '{"progress":80,"content":"模具已更换，去毛刺工序试运行正常","resultType":"PROGRESS"}' >/dev/null
call POST "/tasks/$TASK_ID/checkin" "$T_ADMIN" \
  '{"progress":95,"content":"首件三检与批次复检合格，去毛刺工序已固化到作业指导书","resultType":"RESULT"}' >/dev/null
expect_code 'TC-M4-02 结果类追踪成功' 0
call POST "/tasks/$TASK_ID/submit" "$T_ADMIN" \
  '{"resultSummary":"已更换磨损模具并增加去毛刺工序，首件三检与复检记录齐全，批次返工后全检合格"}' >/dev/null
expect_code 'TC-M4-02 提交验收成功' 0
call POST "/tasks/$TASK_ID/verify" "$T_ADMIN" \
  '{"decision":"PASS","resultSummary":"整改到位，毛刺高度复测合格，同意关闭"}' >/dev/null
expect_code 'TC-M4-02 验收通过' 0
call GET "/tasks/$TASK_ID" "$T_ADMIN" >/dev/null
expect 'TC-M4-02 样本工单已完成' 'DONE' "$(jexpr "d['task']['status']")"

code=$(call POST '/kb/documents/history?deptId=2&limit=100' "$T_ADMIN")
expect_http 'TC-M4-02 历史沉淀接口成功' 200 "$code"
expect_code 'TC-M4-02 历史沉淀业务码成功' 0
CREATED=$(jexpr "d['created']")
expect 'TC-M4-02 沉淀出新文档' 'true' "$([ "${CREATED:-0}" -ge 1 ] && echo true || echo false)"
call GET "/kb/documents?keyword=$SUF" "$T_ADMIN" >/dev/null
HIST_ID=$(jexpr "next((x['id'] for x in d if x['domain']=='HISTORY'), '')")
expect 'TC-M4-02 按本轮标识定位到沉淀文档' 'true' "$([ -n "$HIST_ID" ] && echo true || echo empty)"
expect 'TC-M4-02 沉淀文档为历史知识域' 'HISTORY' "$(doc_field "$HIST_ID" domain)"
expect 'TC-M4-02 沉淀文档解析就绪' 'READY' "$(doc_field "$HIST_ID" syncStatus)"
expect 'TC-M4-02 沉淀文档按部门可见' 'DEPT' "$(doc_field "$HIST_ID" visibility)"
HIST_CHUNKS=$(doc_field "$HIST_ID" chunkCnt)
expect 'TC-M4-02 沉淀文档切片非空' 'true' \
  "$([ "${HIST_CHUNKS:-0}" -gt 0 ] && echo true || echo false)"

code=$(call POST '/kb/documents/history?deptId=2&limit=100' "$T_ADMIN")
expect_http 'TC-M4-02 二次沉淀接口成功' 200 "$code"
expect 'TC-M4-02 二次沉淀不重复建文档' '0' "$(jexpr "d['created']")"
expect 'TC-M4-02 二次沉淀全部按哈希跳过' 'true' "$(jexpr "d['skipped'] == d['scanned']")"

call POST '/kb/search' "$T_ADMIN" '{"question":"毛刺超标怎么处理","domain":"HISTORY","topK":8}' >/dev/null
expect_code 'TC-M4-02 历史知识域检索成功' 0
expect 'TC-M4-02 检索降级标识为本地全文' 'true' "$(jexpr "d['degraded']")"
expect 'TC-M4-02 同题可召回历史措施' 'true' \
  "$(jexpr "d['total'] >= 1 and any(('$SUF' in h['docTitle']) or ('去毛刺' in h['content']) for h in d['hits'])")"
HIT_ID=$(jexpr "next((h['chunkId'] for h in d['hits'] if '去毛刺' in h['content']), '')")
if [ -z "$HIT_ID" ]; then
  HIT_ID=$(jexpr "d['hits'][0]['chunkId'] if d['hits'] else ''")
fi
expect 'TC-M4-02 命中带可回溯切片标识' 'true' "$([ -n "$HIT_ID" ] && echo true || echo empty)"
call GET "/kb/chunks/$HIT_ID" "$T_ADMIN" >/dev/null
expect_code 'TC-M4-02 按切片标识取回原文' 0
expect 'TC-M4-02 原文含措施关键字' 'true' \
  "$(jexpr "'去毛刺' in (d['content'] or '') or '毛刺' in (d['content'] or '')")"
expect 'TC-M4-02 当前切片未被替换' 'false' "$(jexpr "d['superseded']")"

# ---------------------------------------------------------------- M4-03 / M4-04
section 'M4-03 / M4-04 带引用问答事件流与模型不可用降级'
cat > "$TMP/ask.json" <<EOF
{"question":"毛刺超标怎么处理","domain":"HISTORY","topK":6}
EOF
ASK_OUT="$TMP/ask.sse"
ASK_CODE=$(curl -s -N --max-time 40 -o "$ASK_OUT" -w '%{http_code}' -X POST "$B/kb/ask" \
  -H "Authorization: Bearer $T_ADMIN" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' -d @"$TMP/ask.json")
expect_http 'TC-M4-04 问答事件流返回 200' 200 "$ASK_CODE"
if grep -q 'event:retrieved' "$ASK_OUT"; then
  pass 'TC-M4-04 事件流先下发召回结果'
else
  fail 'TC-M4-04 事件流先下发召回结果' "$(head -c 200 "$ASK_OUT")"
fi
RETRIEVE_TOTAL=$(python3 - "$ASK_OUT" <<'PY'
import json,re,sys
text=open(sys.argv[1],encoding='utf-8').read()
m=re.search(r'event:retrieved\s*\ndata:(.*?)\n', text)
if not m:
    print(-1)
else:
    print(json.loads(m.group(1)).get('total', -1))
PY
)
expect 'TC-M4-04 召回结果非空' 'true' "$([ "$RETRIEVE_TOTAL" -ge 1 ] && echo true || echo false)"
if grep -q 'event:error' "$ASK_OUT" && grep -q 'TL-5031' "$ASK_OUT"; then
  pass 'TC-M4-04 模型不可用时下发 TL-5031 并结束'
  skip 'TC-M4-03 真实模型生成答案与引用' '未配置 ARK_API_KEY，待密钥环境复跑'
elif grep -q 'event:done' "$ASK_OUT"; then
  pass 'TC-M4-03 模型可用时答案流结束'
  if grep -q 'event:citations' "$ASK_OUT"; then
    pass 'TC-M4-03 答案附带可回溯引用'
  else
    fail 'TC-M4-03 答案附带可回溯引用' '事件流没有 citations'
  fi
  if grep -q 'event:delta' "$ASK_OUT"; then
    pass 'TC-M4-03 答案按增量流式返回'
  else
    fail 'TC-M4-03 答案按增量流式返回' '事件流没有 delta'
  fi
else
  fail 'TC-M4-04 模型不可用时下发 TL-5031 并结束' "$(head -c 200 "$ASK_OUT")"
  fail 'TC-M4-03 模型可用时答案流结束' "$(head -c 200 "$ASK_OUT")"
fi

# ---------------------------------------------------------------- M4-05
section 'M4-05 问题长度边界返回 422 TL-4228'
code=$(call POST '/kb/ask' "$T_ADMIN" '{"question":"毛刺"}')
expect_http 'TC-M4-05 两个字的提问被拒绝' 422 "$code"
expect_code 'TC-M4-05 过短问题业务码为 TL-4228' 'TL-4228' "$(jget code)"
python3 - "$TMP/long.json" <<'PY'
import json,sys
open(sys.argv[1],'w',encoding='utf-8').write(json.dumps({"question":"毛"*501}, ensure_ascii=False))
PY
code=$(call_json POST '/kb/ask' "$T_ADMIN" "$TMP/long.json")
expect_http 'TC-M4-05 超长提问被拒绝' 422 "$code"
expect_code 'TC-M4-05 超长问题业务码为 TL-4228' 'TL-4228' "$(jget code)"
code=$(call POST '/kb/ask' "$T_ADMIN" '{"question":"   "}')
expect_http 'TC-M4-05 空白提问被拒绝' 422 "$code"
expect_code 'TC-M4-05 空白问题业务码为 TL-4228' 'TL-4228' "$(jget code)"
code=$(call POST '/kb/search' "$T_ADMIN" '{"question":"毛刺"}')
expect_http 'TC-M4-05 检索不受最短限制约束' 200 "$code"

# ---------------------------------------------------------------- M4-06
section 'M4-06 数据范围：执行者查其它部门私有文档零命中'
call POST '/kb/search' "$T_EXEC" '{"question":"毛刺超标怎么处理","topK":8}' >/dev/null
expect_code 'TC-M4-06 执行者检索成功' 0
expect 'TC-M4-06 结果不含其它部门私有文档' 'true' \
  "$(jexpr "all('$SUF' not in h['docTitle'] for h in d['hits'])")"
call POST '/kb/search' "$T_EXEC" '{"question":"毛刺超标怎么处理","deptId":2,"topK":8}' >/dev/null
expect 'TC-M4-06 指定范围外部门时零命中' '0' "$(jexpr "d['total']")"
call POST '/kb/search' "$T_ADMIN" "{\"question\":\"$MARK\",\"topK\":8}" >/dev/null
expect 'TC-M4-06 本轮文档在管理员范围内可检索' 'true' \
  "$(jexpr "d['total'] >= 1 and any('$SUF' in h['docTitle'] for h in d['hits'])")"
call POST '/kb/search' "$T_EXEC" "{\"question\":\"$MARK\",\"topK\":8}" >/dev/null
expect 'TC-M4-06 按本轮独有标识检索也零命中' '0' "$(jexpr "d['total']")"
call GET "/kb/documents?keyword=$SUF" "$T_EXEC" >/dev/null
expect 'TC-M4-06 文档列表不泄露其它部门标题' '0' \
  "$(jexpr "sum(1 for x in d if '$SUF' in x['title'])")"
code=$(call GET "/kb/chunks/$HIT_ID" "$T_EXEC")
expect_http 'TC-M4-06 越权取切片原文被拒绝' 403 "$code"
expect_code 'TC-M4-06 越权切片业务码为 TL-4032' 'TL-4032' "$(jget code)"

# ---------------------------------------------------------------- M4-07
section 'M4-07 重解析生成新快照、旧切片可回溯、解析中拒绝重复触发'
OLD_IDS=$(sql "select id from kb_chunk where doc_id=$DOC_A and superseded=0" | paste -sd, -)
OLD_COUNT=$(sql "select count(*) from kb_chunk where doc_id=$DOC_A and superseded=0")
code=$(call POST "/kb/documents/$DOC_A/sync" "$T_ADMIN")
expect_http 'TC-M4-07 重新解析被受理' 200 "$code"
if poll_sync "$DOC_A" READY 40; then
  pass 'TC-M4-07 重新解析后回到就绪'
else
  fail 'TC-M4-07 重新解析后回到就绪' "最终状态 $(jexpr "d['syncStatus']")"
fi
call GET "/kb/documents/$DOC_A/sync" "$T_ADMIN" >/dev/null
NEW_COUNT=$(jexpr "d['chunkCnt']")
ACTIVE=$(sql "select count(*) from kb_chunk where doc_id=$DOC_A and superseded=0")
expect 'TC-M4-07 新切片数量与文档计数一致' "$NEW_COUNT" "$ACTIVE"
expect 'TC-M4-07 旧切片全部标记为被替换' "$OLD_COUNT" \
  "$(sql "select count(*) from kb_chunk where id in ($OLD_IDS) and superseded=1")"
OLD_ONE=$(echo "$OLD_IDS" | cut -d, -f1)
call GET "/kb/chunks/$OLD_ONE" "$T_ADMIN" >/dev/null
expect_code 'TC-M4-07 旧切片快照仍可按 ID 取回' 0
expect 'TC-M4-07 旧切片标记 superseded' 'true' "$(jexpr "d['superseded']")"
expect 'TC-M4-07 旧切片正文仍可展示' 'true' \
  "$(jexpr "len(d['content'] or '') > 0")"

ARTIFICIAL_PARSE_DOC=$DOC_A
sql "update kb_document set sync_status='PARSING' where id=$DOC_A;"
code=$(call POST "/kb/documents/$DOC_A/sync" "$T_ADMIN")
expect_http 'TC-M4-07 解析中重复触发被拒绝' 409 "$code"
expect_code 'TC-M4-07 解析中重复触发业务码为 TL-4091' 'TL-4091' "$(jget code)"
sql "update kb_document set sync_status='READY' where id=$DOC_A;"
ARTIFICIAL_PARSE_DOC=''

# ---------------------------------------------------------------- 汇总
section '汇总'
printf '通过 %d 项，失败 %d 项，跳过 %d 项\n' "$PASS" "$FAIL" "$SKIP"
if [ ${#FAIL_LIST[@]} -gt 0 ]; then
  printf '\n失败明细：\n'
  printf '  - %s\n' "${FAIL_LIST[@]}"
fi
if [ ${#SKIP_LIST[@]} -gt 0 ]; then
  printf '\n跳过明细：\n'
  printf '  - %s\n' "${SKIP_LIST[@]}"
fi
printf '\n本轮样本：制度文档 #%s，历史文档 #%s，工单 %s\n' "$DOC_A" "$HIST_ID" "$TASK_NO"
[ "$FAIL" -eq 0 ] || exit 1
