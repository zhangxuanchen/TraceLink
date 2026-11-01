#!/usr/bin/env bash
# TraceLink M5 验收入口。对应用例见 docs/TEST-CASES.md 第 10 章 TC-M5-01 ~ TC-M5-09。
# 用法：bash scripts/acceptance-m5.sh
# 前置：后端已启动在 8090、Flyway 已到 V11、MySQL 可用（root/tracelink，库 tracelink）。
# 说明：未配置 ARK_API_KEY 时断言 degraded=true（规则回答），配置后断言模型回答。
# 副作用：会创建并清理【M5验收】会话、无主责在办单；重生成 2026-10 评价后比对统计口径；
#        会清空待确认建议后重新生成；会采纳 1 条措施建议（新建复盘单）与 1 条期限建议（改期），
#        并提交 1 条评价申诉。
#        都是产品内的确定性动作，用于验证 TC-M5-05/06/07/08。
set -uo pipefail

B=${B:-http://127.0.0.1:8090/api/v1}
TMP=$(mktemp -d)
PASS=0
FAIL=0
SKIP=0
FAIL_LIST=()
SKIP_LIST=()

pass() { PASS=$((PASS + 1)); printf '  \033[32m✔\033[0m %s\n' "$1"; }
fail() { FAIL=$((FAIL + 1)); FAIL_LIST+=("$1 — $2"); printf '  \033[31m✘\033[0m %s：%s\n' "$1" "$2"; }
skip() { SKIP=$((SKIP + 1)); SKIP_LIST+=("$1 — $2"); printf '  \033[33m-\033[0m %s：%s\n' "$1" "$2"; }
section() { printf '\n\033[1m%s\033[0m\n' "$1"; }

if ! command -v mysql >/dev/null 2>&1; then
  echo '需要 mysql 客户端造数与断言（chat_session / evaluation / ai_suggestion）' >&2
  exit 2
fi

sql() { mysql -N --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uroot -p'tracelink' tracelink -e "$1" 2>/dev/null; }

cleanup_sessions() {
  local ids
  ids=$(sql "select id from chat_session where title like '【M5验收】%'" | paste -sd, -)
  [ -z "$ids" ] && return 0
  sql "delete from chat_message where session_id in ($ids); delete from chat_session where id in ($ids);"
}

cleanup_seed_tasks() { # 只清本脚本造的无主责在办单，不动产品工单
  local ids
  ids=$(sql "select id from task where title like '【M5验收】%'" | paste -sd, -)
  [ -z "$ids" ] && return 0
  sql "delete from task_gate_snapshot where task_id in ($ids);
       delete from task_checkin where task_id in ($ids);
       delete from task_assignee where task_id in ($ids);
       delete from task_issue where task_id in ($ids);
       delete from task_action where task_id in ($ids) or task_child_id in ($ids);
       delete from task_metric where task_id in ($ids);
       delete from notification where task_id in ($ids);
       delete from ai_suggestion where target_id in ($ids);
       delete from evaluation where target_type='TASK' and target_id in ($ids);
       delete from audit_log where biz_type='TASK' and biz_id in ($ids);
       delete from task where id in ($ids);"
}

cleanup_previous() { cleanup_sessions; cleanup_seed_tasks; }
trap cleanup_previous EXIT

reset_evaluations() { # 让本轮重算 2026-10 评价，保证 TC-M5-06 与统计口径同源
  sql "delete from evaluation where period='2026-10'"
}

reset_pending_suggestions() { # 只清待确认建议，保证本轮验证的是本次生成的可执行草稿
  sql "delete from ai_suggestion where status='PENDING'"
}

call() { # METHOD PATH TOKEN [BODY] -> HTTP 状态码，响应落到 $TMP/body.json
  local method=$1 path=$2 token=$3 body=${4:-}
  local args=(-s -o "$TMP/body.json" -w '%{http_code}' -X "$method" "$B$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  curl "${args[@]}"
}

jget() {
  python3 - "$TMP/body.json" "$1" <<'PY'
import json,sys
try:
    print(json.load(open(sys.argv[1])).get(sys.argv[2], ""))
except Exception:
    print('')
PY
}

jexpr() { # 对 data 求值，如 d['id']；布尔输出 true/false
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

ask_sse() { # SID TOKEN QUESTION -> HTTP 状态码，事件流落到 $TMP/sse.txt
  local sid=$1 token=$2 question=$3
  python3 - "$TMP/payload.json" "$question" <<'PY'
import json,sys
json.dump({'question': sys.argv[2], 'allowTools': True}, open(sys.argv[1], 'w'), ensure_ascii=False)
PY
  curl -s -N -o "$TMP/sse.txt" -w '%{http_code}' -X POST "$B/chat/sessions/$sid/messages" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d @"$TMP/payload.json"
}

sse_events() { grep '^event:' "$TMP/sse.txt" | sed 's/event:/ /' | tr -d '\r' | awk '{print $1}' | paste -sd, -; }

sse_event_data() { # EVENT -> JSON data 原文（取最后一个同名事件）
  python3 - "$TMP/sse.txt" "$1" <<'PY'
import sys
text=open(sys.argv[1]).read().replace('\r','')
blocks=text.split('\n\n')
found=''
for block in blocks:
    lines=block.split('\n')
    event=''
    data=[]
    for line in lines:
        if line.startswith('event:'): event=line[6:].strip()
        elif line.startswith('data:'): data.append(line[5:].lstrip())
    if event==sys.argv[2] and data:
        found='\n'.join(data)
print(found)
PY
}

sse_tool_lines() { # name + args JSON，每行一个
  python3 - "$TMP/sse.txt" <<'PY'
import json,sys,re
text=open(sys.argv[1]).read().replace('\r','')
for block in text.split('\n\n'):
    m=re.search(r'^event:(.*)$', block, re.M)
    if not m or m.group(1).strip()!='tool_call':
        continue
    data='\n'.join(line[5:].lstrip() for line in block.splitlines() if line.startswith('data:'))
    if not data:
        continue
    obj=json.loads(data)
    print(obj.get('name','') + '\t' + json.dumps(obj.get('args') or {}, ensure_ascii=False, separators=(',',':')))
PY
}

sse_tools_ok() { # 期望四类工具名
  python3 - "$TMP/sse.txt" <<'PY'
import json,sys,re
need={'task_query','stats_query','employee_query','knowledge_search'}
text=open(sys.argv[1]).read().replace('\r','')
names=set()
for block in text.split('\n\n'):
    m=re.search(r'^event:(.*)$', block, re.M)
    if not m or m.group(1).strip()!='tool_call':
        continue
    data='\n'.join(line[5:].lstrip() for line in block.splitlines() if line.startswith('data:'))
    if data:
        names.add(json.loads(data).get('name'))
missing=need-names
print('ok' if not missing else 'missing:'+','.join(sorted(missing)))
PY
}

sse_order_ok() {
  python3 - "$TMP/sse.txt" <<'PY'
import sys
text=open(sys.argv[1]).read().replace('\r','')
events=[]
for block in text.split('\n\n'):
    for line in block.split('\n'):
        if line.startswith('event:'):
            events.append(line[6:].strip())
            break
def first(name):
    return events.index(name) if name in events else -1
r=first('retrieval'); t=first('tool_call'); d=first('delta'); c=first('citations'); e=first('done')
print('ok' if 0 <= r < t < d < c < e else f'bad:{events}')
PY
}

sse_done_value() { # KEY
  python3 - "$TMP/sse.txt" "$1" <<'PY'
import json,sys,re
text=open(sys.argv[1]).read().replace('\r','')
for block in text.split('\n\n'):
    m=re.search(r'^event:(.*)$', block, re.M)
    if not m or m.group(1).strip()!='done':
        continue
    data='\n'.join(line[5:].lstrip() for line in block.splitlines() if line.startswith('data:'))
    if data:
        value=json.loads(data).get(sys.argv[2], '')
        print(value if isinstance(value, str) else json.dumps(value, ensure_ascii=False))
        break
PY
}

sse_citations_value() { # KEY 或 count
  python3 - "$TMP/sse.txt" <<'PY'
import json,sys,re
text=open(sys.argv[1]).read().replace('\r','')
for block in text.split('\n\n'):
    m=re.search(r'^event:(.*)$', block, re.M)
    if not m or m.group(1).strip()!='citations':
        continue
    data='\n'.join(line[5:].lstrip() for line in block.splitlines() if line.startswith('data:'))
    if not data:
        continue
    arr=json.loads(data)
    print(len(arr), arr[0].get('chunkId', '') if arr else '')
    break
PY
}

T_ADMIN=$(login admin)
T_OWNER=$(login xuwei)
T_EXEC=$(login wangxb)
if [ -z "$T_ADMIN" ] || [ -z "$T_OWNER" ] || [ -z "$T_EXEC" ]; then
  echo '登录失败：确认后端 8090 可用、种子账号 admin/xuwei/wangxb 口令 admin123' >&2
  exit 2
fi

cleanup_previous

# ---------------------------------------------------------------- TC-M5-01
section 'TC-M5-01 会话归属与归档'
code=$(call POST '/chat/sessions' "$T_ADMIN" '{"title":"【M5验收】归属与归档"}')
expect_http '管理员建会话返回 201' 201 "$code"
SID_A=$(jexpr "d['id']")
expect '建会话返回 id' 'true' "$([ -n "$SID_A" ] && echo true || echo false)"

code=$(call GET "/chat/sessions/$SID_A/messages" "$T_OWNER")
expect_http '负责人读他人会话被拒' 403 "$code"
expect_code '负责人读他人会话错误码' 'TL-4032'

code=$(call GET '/chat/sessions?limit=50' "$T_ADMIN")
OWN_HAS=$(python3 - "$TMP/body.json" "$SID_A" <<'PY'
import json,sys
try:
    ids=[str(s['id']) for s in json.load(open(sys.argv[1])).get('data') or []]
    print('true' if sys.argv[2] in ids else 'false')
except Exception:
    print('false')
PY
)
expect '列表只回本人会话（管理员可见）' 'true' "$OWN_HAS"

code=$(call POST "/chat/sessions/$SID_A/archive" "$T_ADMIN")
expect_http '归档会话返回 200' 200 "$code"
expect '归档后状态' 'ARCHIVED' "$(jexpr "d['status']")"

code=$(call POST "/chat/sessions/$SID_A/messages" "$T_ADMIN" '{"question":"归档后还能提问吗？"}')
expect_http '归档后提问被拒' 409 "$code"
expect_code '归档后提问错误码' 'TL-4091'

code=$(call GET '/chat/sessions?limit=50' "$T_OWNER")
OWNER_HAS=$(python3 - "$TMP/body.json" "$SID_A" <<'PY'
import json,sys
try:
    ids=[str(s['id']) for s in json.load(open(sys.argv[1])).get('data') or []]
    print('true' if sys.argv[2] in ids else 'false')
except Exception:
    print('false')
PY
)
expect '负责人列表不含他人会话' 'false' "$OWNER_HAS"

# ---------------------------------------------------------------- TC-M5-02
section 'TC-M5-02 流式回答与工具调用可见'
code=$(call POST '/chat/sessions' "$T_ADMIN" '{"title":"【M5验收】流式与工具"}')
SID_B=$(jexpr "d['id']")
Q_ADMIN='生产部这周还有几张单没验收？这个月完成率和逾期率的统计怎么样？张厚生现在有多少在办工单？冲压工位毛刺超标制度里怎么处理？'
code=$(ask_sse "$SID_B" "$T_ADMIN" "$Q_ADMIN")
expect_http '提问事件流返回 200' 200 "$code"
EVENTS=$(sse_events)
expect '事件含 retrieval/tool_call/delta/citations/done' \
  'true' "$(python3 - "$EVENTS" <<'PY'
import sys
e=set(sys.argv[1].split(','))
print('true' if {'retrieval','tool_call','delta','citations','done'} <= e else 'false')
PY
)"
ORDER=$(sse_order_ok)
expect '事件顺序 retrieval→tool_call→delta→citations→done' 'ok' "$ORDER"
TOOLS=$(sse_tools_ok)
expect '四类只读工具都被调用' 'ok' "$TOOLS"
expect '工具调用含结果条数与耗时' 'true' "$(grep -c 'resultCount' "$TMP/sse.txt" | awk '{print ($1>0)?"true":"false"}')"
ANSWER_CHARS=$(sse_done_value answerChars)
expect 'done 带回答长度' 'true' "$([ "${ANSWER_CHARS:-0}" -gt 0 ] && echo true || echo false)"

# ---------------------------------------------------------------- TC-M5-03
section 'TC-M5-03 四类工具的数据范围（执行者）'
code=$(call POST '/chat/sessions' "$T_EXEC" '{"title":"【M5验收】执行者范围"}')
SID_E=$(jexpr "d['id']")
code=$(ask_sse "$SID_E" "$T_EXEC" "$Q_ADMIN")
expect_http '执行者提问返回 200' 200 "$code"
TOOLS_EXEC=$(sse_tools_ok)
expect '执行者也触发四类工具' 'ok' "$TOOLS_EXEC"
TASK_ARGS=$(sse_tool_lines | awk -F'\t' '$1=="task_query"{print $2; exit}')
expect '执行者越权部门被裁剪（task_query）' 'true' "$(echo "$TASK_ARGS" | grep -q '"clamped":true' && echo true || echo false)"
expect '裁剪后不保留越权 deptId' 'true' "$(echo "$TASK_ARGS" | grep -q '"deptId":2' && echo false || echo true)"
STATS_ARGS=$(sse_tool_lines | awk -F'\t' '$1=="stats_query"{print $2; exit}')
expect '执行者统计按本人聚合' 'true' "$(echo "$STATS_ARGS" | grep -q '"employeeId"' && echo true || echo false)"
EMP_ARGS=$(sse_tool_lines | awk -F'\t' '$1=="employee_query"{print $2; exit}')
expect '执行者人员只查本人可见范围' 'true' "$(echo "$EMP_ARGS" | grep -q '"names":\[\]' && echo true || echo false)"

# ---------------------------------------------------------------- TC-M5-04
section 'TC-M5-04 引用回溯'
read -r CIT_COUNT CIT_CHUNK <<<"$(sse_citations_value)"
if [ "${CIT_COUNT:-0}" -gt 0 ]; then
  pass "回答带引用（$CIT_COUNT 条）"
  code=$(call GET "/kb/chunks/$CIT_CHUNK" "$T_ADMIN")
  expect_http '引用切片可取原文' 200 "$code"
  CONTENT_LEN=$(jexpr "len(d['content'] or '')")
  expect '原文正文非空' 'true' "$([ "${CONTENT_LEN:-0}" -gt 10 ] && echo true || echo false)"
  code=$(call GET "/chat/sessions/$SID_B/messages?limit=100" "$T_ADMIN")
  PERSIST=$(python3 - "$TMP/body.json" <<'PY'
import json,sys
rows=json.load(open(sys.argv[1])).get('data') or []
a=[r for r in rows if r.get('role')=='ASSISTANT']
cits=(a[-1].get('citations') or []) if a else []
print(len(cits))
PY
)
  expect '会话历史可再次点开引用' 'true' "$([ "${PERSIST:-0}" -gt 0 ] && echo true || echo false)"
else
  skip '引用回溯' '本轮问题没有命中知识片段（可先补知识文档）'
fi

# ---------------------------------------------------------------- TC-M5-05
section 'TC-M5-05 评价可下钻证据与周期唯一'
reset_evaluations
code=$(call POST '/evaluations/run' "$T_ADMIN" '{"period":"2026-10"}')
expect_http '评价生成返回 200' 200 "$code"
expect_code '评价生成成功' '0'
E1=$(jexpr "d['employeeCreated']")
T1=$(jexpr "d['taskCreated']")
K1=$(jexpr "d['skipped']")
expect '评价生成含人员或任务' 'true' "$( [ $(( ${E1:-0} + ${T1:-0} )) -gt 0 ] && echo true || echo false )"

code=$(call POST '/evaluations/run' "$T_ADMIN" '{"period":"2026-10"}')
E2=$(jexpr "d['employeeCreated']")
T2=$(jexpr "d['taskCreated']")
K2=$(jexpr "d['skipped']")
expect '同周期重复触发不重复生成' '0' "$E2"
expect '同周期重复触发计入 skipped' 'true' "$([ "${K2:-0}" -gt 0 ] && echo true || echo false)"

code=$(call GET '/evaluations?period=2026-10&targetType=EMPLOYEE&limit=50' "$T_ADMIN")
PERSON_EVAL=$(python3 - "$TMP/body.json" <<'PY'
import json,sys
rows=json.load(open(sys.argv[1])).get('data') or []
for r in rows:
    ev=r.get('evidence') or {}
    if ev.get('taskIds'):
        print(r['id']); break
PY
)
if [ -n "$PERSON_EVAL" ]; then
  code=$(call GET "/evaluations/$PERSON_EVAL" "$T_ADMIN")
  DIMS=$(jexpr "len(d['scores'])")
  LINKS=$(jexpr "len((d['evidence'] or {}).get('taskIds') or [])")
  expect '人员评价六维分数' 'true' "$([ "${DIMS:-0}" -ge 6 ] && echo true || echo false)"
  expect '人员评价证据含工单链接' 'true' "$([ "${LINKS:-0}" -gt 0 ] && echo true || echo false)"
else
  skip '人员评价证据' '该周期没有带工单证据的人员评价'
fi

code=$(call GET '/evaluations?period=2026-10&targetType=TASK&limit=50' "$T_ADMIN")
TASK_EVAL=$(jexpr "(d[0] or {}).get('id') if d else ''")
if [ -n "$TASK_EVAL" ]; then
  code=$(call GET "/evaluations/$TASK_EVAL" "$T_ADMIN")
  TASK_DIMS=$(jexpr "len(d['scores'])")
  TASK_EVID=$(jexpr "1 if (d['evidence'] or {}).get('taskId') else 0")
  expect '任务评价四维分数' 'true' "$([ "${TASK_DIMS:-0}" -ge 4 ] && echo true || echo false)"
  expect '任务评价证据含工单 ID' '1' "$TASK_EVID"
else
  skip '任务评价证据' '该周期没有任务评价'
fi

code=$(call GET '/evaluations?period=2026-10&targetType=EMPLOYEE&limit=50' "$T_ADMIN")
APPEAL_ID=$(python3 - "$TMP/body.json" <<'PY'
import json,sys
rows=json.load(open(sys.argv[1])).get('data') or []
for r in rows:
    if r.get('status')=='PUBLISHED':
        print(r['id']); break
PY
)
if [ -n "$APPEAL_ID" ]; then
  code=$(call POST "/evaluations/$APPEAL_ID/appeal" "$T_ADMIN" '{"reason":"本周期跨部门协办口径与统计看板不一致，申请复核"}')
  expect_http '提交申诉返回 200' 200 "$code"
  expect '申诉后状态' 'APPEALED' "$(jexpr "d['status']")"
  REASON=$(jexpr "(d['evidence'] or {}).get('appealReason')")
  expect '申诉理由写入证据' 'true' "$([ -n "$REASON" ] && echo true || echo false)"
  AUDIT_CNT=$(sql "select count(*) from audit_log where action='EVAL_APPEAL' and biz_id=$APPEAL_ID")
  expect '申诉写审计' 'true' "$([ "${AUDIT_CNT:-0}" -gt 0 ] && echo true || echo false)"
else
  skip '评价申诉' '该周期没有可申诉（PUBLISHED）的评价'
fi

# ---------------------------------------------------------------- TC-M5-06
section 'TC-M5-06 评价口径与统计一致性'
code=$(call GET '/stats/person?from=2026-10-01&to=2026-10-31' "$T_ADMIN")
cp "$TMP/body.json" "$TMP/stats-person.json"
code=$(call GET '/evaluations?period=2026-10&targetType=EMPLOYEE&limit=50' "$T_ADMIN")
cp "$TMP/body.json" "$TMP/eval-list.json"
CONSIST=$(python3 - "$TMP/stats-person.json" "$TMP/eval-list.json" <<'PY'
import json,sys
stats=json.load(open(sys.argv[1])).get('data') or {}
evals=json.load(open(sys.argv[2])).get('data') or []
persons={p.get('name'): p for p in stats.get('persons') or []}
checked=0
bad=0
for e in evals:
    metrics=(e.get('evidence') or {}).get('metrics') or {}
    p=persons.get(e.get('targetName'))
    if not p or not metrics:
        continue
    checked+=1
    for k in ('created','due','done','open','overdue'):
        if int(metrics.get(k,0)) != int(p.get(k,0)):
            bad+=1
    for k in ('onTimeRate','evidenceRate'):
        if abs(float(metrics.get(k,0)) - float(p.get(k,0))) > 0.01:
            bad+=1
print(f'{checked}:{bad}')
PY
)
CHECKED=${CONSIST%%:*}
BAD=${CONSIST##*:}
expect '同一区间同一人数字与 stats/person 一致' '0' "$BAD"
expect '比对覆盖到人员' 'true' "$([ "${CHECKED:-0}" -gt 0 ] && echo true || echo false)"

# ---------------------------------------------------------------- TC-M5-07
section 'TC-M5-07 五类建议与采纳'
# 派工建议要求“在办且无主责”，先造一张单再把主责置空，跑完在 EXIT 清理
SEED_TITLE="【M5验收】无主责在办单 $(date '+%H%M%S')"
PLAN_END=$(date -v+7d '+%Y-%m-%dT18:00:00' 2>/dev/null || date -d '+7 day' '+%Y-%m-%dT18:00:00')
code=$(call POST '/tasks' "$T_ADMIN" "{\"title\":\"$SEED_TITLE\",\"type\":\"QUALITY\",\"deptId\":3,\"ownerId\":1,\"primaryAssigneeId\":3,\"priority\":2,\"planEnd\":\"$PLAN_END\"}")
if [ "$code" = 201 ]; then
  SEED_TASK=$(jexpr "d['taskId']")
  sql "update task set primary_assignee_id=null where id=$SEED_TASK"
  pass '造一张无主责在办单（派工建议样本）'
else
  fail '造一张无主责在办单' "HTTP $code $(jget message)"
fi

reset_pending_suggestions
code=$(call POST '/ai/suggestions/run' "$T_ADMIN")
expect_http '建议生成返回 200' 200 "$code"
expect_code '建议生成成功' '0'
TYPES=$(sql "select count(distinct type) from ai_suggestion where type in ('DISPATCH','ACTION','DUE_DATE','ESCALATE','POLICY_GAP')")
expect '五类建议均已落库' '5' "$TYPES"
if [ -n "${SEED_TASK:-}" ]; then
  DISPATCH_IID=$(sql "select id from ai_suggestion where type='DISPATCH' and status='PENDING' and target_id=$SEED_TASK order by id desc limit 1")
  expect '无主责工单生成派工建议' 'true' "$([ -n "$DISPATCH_IID" ] && echo true || echo false)"
  if [ -n "$DISPATCH_IID" ]; then
    CAND=$(sql "select count(*) from ai_suggestion where id=$DISPATCH_IID and payload like '%primaryAssigneeId%'")
    expect '派工建议带可执行候选' 'true' "$([ "${CAND:-0}" -gt 0 ] && echo true || echo false)"
  fi
fi

# 来源单若已建过复盘单，再采纳会撞同标题唯一（TL-4092），优先挑没有复盘单的来源
ACTION_ID=$(sql "select s.id from ai_suggestion s join task t on t.id=s.target_id
  where s.type='ACTION' and s.status='PENDING'
    and not exists (select 1 from task x where x.deleted=0
                    and x.title like concat('复盘：%', t.task_no, '%'))
  order by s.id limit 1")
[ -z "$ACTION_ID" ] && ACTION_ID=$(sql "select id from ai_suggestion where type='ACTION' and status='PENDING' order by id limit 1")
DUE_ID=$(sql "select id from ai_suggestion where type='DUE_DATE' and status='PENDING' order by id limit 1")

if [ -n "$ACTION_ID" ]; then
  CODE_BEFORE=$(sql "select count(*) from ai_suggestion where status='PENDING'")
  code=$(call POST "/ai/suggestions/$ACTION_ID/accept" "$T_ADMIN")
  expect_http '采纳措施建议返回 200' 200 "$code"
  ACC_TYPE=$(jexpr "d['resultType']")
  NEW_ID=$(jexpr "d['objectId']")
  NEW_NO=$(jexpr "d['objectNo']")
  expect '措施建议执行建单动作' 'TASK_CREATED' "$ACC_TYPE"
  code=$(call GET "/tasks/$NEW_ID" "$T_ADMIN")
  TITLE=$(jexpr "d['task']['title']")
  STATUS=$(jexpr "d['task']['status']")
  expect '建单走 TSK-1 生成正式工单' 'true' "$(echo "$TITLE" | grep -q '复盘：' && echo true || echo false)"
  expect '新建工单进入待接单' 'PENDING' "$STATUS"
  AUDIT_CREATE=$(sql "select count(*) from audit_log where biz_type='TASK' and action='CREATE' and biz_id=$NEW_ID")
  expect '建单写工单审计' 'true' "$([ "${AUDIT_CREATE:-0}" -gt 0 ] && echo true || echo false)"
  CODE_AFTER=$(sql "select count(*) from ai_suggestion where status='PENDING'")
  expect '采纳后移出待确认队列' 'true' "$([ "${CODE_AFTER:-0}" -lt "${CODE_BEFORE:-0}" ] && echo true || echo false)"
else
  skip '采纳措施建议' '没有待确认措施建议'
fi

if [ -n "$DUE_ID" ]; then
  TARGET=$(sql "select target_id from ai_suggestion where id=$DUE_ID")
  OLD_PLAN=$(sql "select ifnull(date_format(plan_end,'%Y-%m-%d %H:%i'),'') from task where id=$TARGET")
  OLD_DELAY=$(sql "select ifnull(delay_count,0) from task where id=$TARGET")
  code=$(call POST "/ai/suggestions/$DUE_ID/accept" "$T_ADMIN")
  expect_http '采纳期限建议返回 200' 200 "$code"
  expect '期限建议执行改期动作' 'RESCHEDULED' "$(jexpr "d['resultType']")"
  NEW_PLAN=$(sql "select ifnull(date_format(plan_end,'%Y-%m-%d %H:%i'),'') from task where id=$TARGET")
  NEW_DELAY=$(sql "select ifnull(delay_count,0) from task where id=$TARGET")
  expect '改期后计划完成时间变化' 'true' "$([ "$NEW_PLAN" != "$OLD_PLAN" ] && echo true || echo false)"
  expect '改期计数增加' 'true' "$([ "${NEW_DELAY:-0}" -ge $(( ${OLD_DELAY:-0} + 1 )) ] && echo true || echo false)"
  RESCHEDULE_AUDIT=$(sql "select count(*) from audit_log where biz_type='TASK' and action='UPDATE' and biz_id=$TARGET and after like '%RESCHEDULE%'")
  expect '改期可在工单审计/时间线看到' 'true' "$([ "${RESCHEDULE_AUDIT:-0}" -gt 0 ] && echo true || echo false)"
else
  skip '采纳期限建议' '没有待确认期限建议'
fi

# ---------------------------------------------------------------- TC-M5-08
section 'TC-M5-08 采纳后可审计'
if [ -n "${ACTION_ID:-}" ] && [ -n "${NEW_ID:-}" ]; then
  ROW=$(sql "select concat(ifnull(model_name,'-'),'|',ifnull(prompt_ver,'-'),'|',ifnull(token_in,0),'|',ifnull(token_out,0),'|',ifnull(latency_ms,0),'|',ifnull(accepted_by,0),'|',ifnull(date_format(accepted_at,'%Y-%m-%d %H:%i:%s'),''),'|',ifnull(accept_result,'')) from ai_suggestion where id=$ACTION_ID")
  expect '建议带模型名/提示词版本/token/耗时' 'true' "$(echo "$ROW" | awk -F'|' '{print ($1!="-" && $2!="-" && $3>0 && $4>0 && $5>0)?"true":"false"}')"
  expect '采纳记录人与时间' 'true' "$(echo "$ROW" | awk -F'|' '{print ($6>0 && $7!="")?"true":"false"}')"
  expect '采纳结果记录对象 ID/编号' 'true' "$(echo "$ROW" | grep -q "$NEW_ID" && echo true || echo false)"
  ACC_AUDIT=$(sql "select count(*) from audit_log where action='SUGGESTION_ACCEPT' and biz_id=$ACTION_ID")
  expect '采纳写 SUGGESTION_ACCEPT 审计' 'true' "$([ "${ACC_AUDIT:-0}" -gt 0 ] && echo true || echo false)"
else
  skip '采纳审计' '本轮没有采纳措施建议'
fi

# ---------------------------------------------------------------- TC-M5-09
section 'TC-M5-09 降级标记'
DEGRADED=$(sse_done_value degraded)
MODEL=$(sse_done_value model)
if [ "$DEGRADED" = 'true' ]; then
  expect '模型不可用时标记 degraded' 'true' 'true'
  expect '降级回答来源为规则' 'RULE' "$MODEL"
  skip '模型可用路径' '未配置 ARK_API_KEY，规则降级已覆盖；配置后复跑本项断言模型回答'
else
  expect '模型可用时正常回答' 'false' "$DEGRADED"
  expect '模型回答来源非规则' 'true' "$([ "$MODEL" != 'RULE' ] && echo true || echo false)"
fi
# 不影响其它请求：同一会话再问一次仍正常返回 done
code=$(ask_sse "$SID_B" "$T_ADMIN" '再确认一次：生产部这周还有几张单没验收？')
expect_http '降级后同会话再次提问正常' 200 "$code"
ORDER2=$(sse_order_ok)
expect '再次提问事件顺序仍完整' 'ok' "$ORDER2"

# ---------------------------------------------------------------- 汇总
section '汇总'
printf '通过 %d，失败 %d，跳过 %d（临时目录 %s）\n' "$PASS" "$FAIL" "$SKIP" "$TMP"
if [ "$FAIL" -gt 0 ]; then
  printf '失败项：\n'
  printf '  - %s\n' "${FAIL_LIST[@]}"
fi
if [ "$SKIP" -gt 0 ]; then
  printf '跳过项：\n'
  printf '  - %s\n' "${SKIP_LIST[@]}"
fi
[ "$FAIL" -eq 0 ]
