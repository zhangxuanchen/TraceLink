#!/usr/bin/env bash
# TraceLink M3 验收入口。对应用例见 docs/TEST-CASES.md 第 8 章 M3-01 ~ M3-10。
# 用法：bash scripts/acceptance-m3.sh
# 前置：后端已启动在 8090、Flyway 已到 V8、MySQL 可用（root/tracelink，库 tracelink）。
# 可选：LOAD_TASKS=1000 压测工单数量，默认 1000；设 0 跳过压测。
set -uo pipefail

B=${B:-http://127.0.0.1:8090/api/v1}
TMP=$(mktemp -d)
PASS=0
FAIL=0
FAIL_LIST=()
SUF=$(date +%H%M%S)
TODAY=$(date '+%Y-%m-%d')
if date -v-1d '+%Y-%m-%d' >/dev/null 2>&1; then
  YDAY=$(date -v-1d '+%Y-%m-%d')
else
  YDAY=$(date -d yesterday '+%Y-%m-%d')
fi
if date -v+10d '+%Y-%m-%dT18:00:00' >/dev/null 2>&1; then
  PLAN_END=$(date -v+10d '+%Y-%m-%dT18:00:00')
else
  PLAN_END=$(date -d '+10 day' '+%Y-%m-%dT18:00:00')
fi
OLD_DATE=2026-04-01
LOAD_TASKS=${LOAD_TASKS:-1000}

pass() { PASS=$((PASS + 1)); printf '  \033[32m✔\033[0m %s\n' "$1"; }
fail() { FAIL=$((FAIL + 1)); FAIL_LIST+=("$1 — $2"); printf '  \033[31m✘\033[0m %s：%s\n' "$1" "$2"; }
section() { printf '\n\033[1m%s\033[0m\n' "$1"; }

if ! command -v mysql >/dev/null 2>&1; then
  echo '需要 mysql 客户端造数（matter 链、阈值提醒、压测）' >&2
  exit 2
fi

sql() { mysql -N -h127.0.0.1 -P3306 -uroot -p'tracelink' tracelink -e "$1" 2>/dev/null; }
sql_file() { mysql -h127.0.0.1 -P3306 -uroot -p'tracelink' tracelink < "$1" >/dev/null 2>&1; }

ORIG_PATROL_G6=''

cleanup_load() { # 压测工单与它们的快照、提醒一起清掉
  local list
  list=$(sql "select id from task where task_no like 'M3L-%'" | paste -sd, -)
  [ -z "$list" ] && return 0
  sql "delete from notification where task_id in ($list);
       delete from task_gate_snapshot where task_id in ($list);
       delete from task_checkin where task_id in ($list);
       delete from task_issue where task_id in ($list);
       delete from task_action where task_id in ($list);
       delete from task_assignee where task_id in ($list);
       delete from task where id in ($list);"
}

restore_env() { # 无论成败都还原条件配置并清掉压测数据
  if [ -n "$ORIG_PATROL_G6" ]; then
    sql "update gate_rule set weight=$ORIG_PATROL_G6 where task_type='PATROL' and gate_code='G6';"
  fi
  cleanup_load
}
trap restore_env EXIT

cleanup_previous() { # 只清本脚本上一轮留下的【M3验收】与 M3L- 压测单，种子单 1–5 不动
  local list
  list=$(sql "select id from task where (title like '【M3验收】%' or task_no like 'M3L-%') and id > 5" | paste -sd, -)
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

call() { # METHOD PATH TOKEN [BODY] -> 输出 HTTP 状态码，响应体落到 $TMP/body.json
  local method=$1 path=$2 token=$3 body=${4:-}
  local args=(-s -o "$TMP/body.json" -w '%{http_code}' -X "$method" "$B$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  curl "${args[@]}"
}

jget() { # 取顶层字段，如 code
  python3 - "$TMP/body.json" "$1" <<'PY'
import json,sys
print(json.load(open(sys.argv[1])).get(sys.argv[2], ""))
PY
}

jexpr() { # 对 data 求值，如 d['completionRate']；布尔统一输出小写
  python3 - "$TMP/body.json" "$1" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])).get('data')
v=eval(sys.argv[2])
if isinstance(v, bool):
    print('true' if v else 'false')
else:
    print(v)
PY
}

expect() { # 名称 期望 实际
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "期望 [$2]，实际 [$3]"; fi
}

expect_http() {
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "HTTP 期望 $2，实际 $3"; fi
}

expect_code() { # 名称 期望业务码（成功为 0）
  local actual
  actual=$(jget code)
  expect "$1" "$2" "$actual"
}

login() {
  curl -s -X POST "$B/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"admin123\"}" \
    | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('token','') if d.get('code')=='0' else '')"
}

gate_field() { # 条件编码 字段名
  jexpr "(next((g for g in d['gates'] if g['code']=='$1'), None) or {}).get('$2','')"
}

create_task() { # 标题 附加 JSON 片段；结果写 NEW_ID / NEW_NO
  local title=$1 extra=${2:-}
  call POST '/tasks' "$T_ADMIN" "{
    \"title\":\"$title\",\"type\":\"QUALITY\",\"deptId\":3,\"ownerId\":$OWNER_ID,
    \"primaryAssigneeId\":$EXEC_ID,\"priority\":2,\"planEnd\":\"$PLAN_END\"$extra}" >/dev/null
  NEW_ID=$(jexpr "(d or {}).get('taskId','')")
  NEW_NO=$(jexpr "(d or {}).get('taskNo','')")
}

dispatch() { # 工单 ID
  call POST "/tasks/$1/dispatch" "$T_ADMIN" \
    "{\"primaryAssigneeId\":$EXEC_ID,\"planEnd\":\"$PLAN_END\"}" >/dev/null
}

checkin() { # 工单 ID 进度 内容 类型 [附加 JSON 片段]
  local extra=${5:-}
  call POST "/tasks/$1/checkin" "$T_ADMIN" \
    "{\"progress\":$2,\"content\":\"$3\",\"resultType\":\"$4\"$extra}" >/dev/null
}

notif_field() { # 工单号 条件编码 字段；取收件箱里匹配的一条
  jexpr "(next((n for n in d['records'] if n['taskNo']=='$1' and n['gateCode']=='$2'), None) or {}).get('$3','')"
}

notif_count() { # 工单号 [条件编码] 收件箱里匹配的条数
  local gate=${2:-}
  if [ -z "$gate" ]; then
    jexpr "sum(1 for n in d['records'] if n['taskNo']=='$1')"
  else
    jexpr "sum(1 for n in d['records'] if n['taskNo']=='$1' and n['gateCode']=='$2')"
  fi
}

# ---------------------------------------------------------------- 准备
section '准备：登录、健康检查与上一轮数据清理'
cleanup_previous
health=$(curl -s -o /dev/null -w '%{http_code}' "${B%/api/v1}/actuator/health")
expect_http '健康检查返回 200' 200 "$health"
T_ADMIN=$(login admin)
T_OWNER=$(login xuwei)
T_EXEC=$(login wangxb)
expect 'admin 取得令牌' 'non-empty' "$([ -n "$T_ADMIN" ] && echo non-empty || echo empty)"
expect 'xuwei 取得令牌' 'non-empty' "$([ -n "$T_OWNER" ] && echo non-empty || echo empty)"
expect 'wangxb 取得令牌' 'non-empty' "$([ -n "$T_EXEC" ] && echo non-empty || echo empty)"
call GET '/employees/options' "$T_ADMIN" >/dev/null
OWNER_ID=$(jexpr "next((e['id'] for e in d if e['name']=='张厚生'), '')")
EXEC_ID=$(jexpr "next((e['id'] for e in d if e['name']=='王先兵'), '')")
expect '负责人定位到张厚生' 'non-empty' "$([ -n "$OWNER_ID" ] && echo non-empty || echo empty)"
expect '执行者定位到王先兵' 'non-empty' "$([ -n "$EXEC_ID" ] && echo non-empty || echo empty)"

# ---------------------------------------------------------------- M3-01
section 'M3-01 九条条件逐条判定（CMP-1 实时算式）'
create_task "【M3验收】$SUF 九条件主流程" ',"acceptCriteria":["现场整改完成","证据记录齐全"]'
TASK_A=$NEW_ID
NO_A=$NEW_NO
[ -n "$TASK_A" ] && pass "建主流程工单 $NO_A" || fail '建主流程工单' '未返回 taskId'
expect '新建工单为 PENDING' 'PENDING' "$(jexpr "d['status']")"

call GET "/tasks/$TASK_A/completion-map" "$T_ADMIN" >/dev/null
expect_code 'TC-M3-01 单工单地图可读' 0
expect 'TC-M3-01 九条条件齐全且有序' 'G1,G2,G3,G4,G5,G6,G7,G8,G9' \
  "$(jexpr "','.join(g['code'] for g in d['gates'])")"
expect 'TC-M3-01 三档状态取值合法' 'true' \
  "$(jexpr "all(g['stateLabel'] in ('达成','未达成','不适用') for g in d['gates'])")"
expect 'TC-M3-01 未达成行均有承担者、动作、提示' 'true' \
  "$(jexpr "all(g['holderName'] and g['actionUrl'] and g['hint'] for g in d['gates'] if g['state']=='TODO')")"
expect 'TC-M3-01 未接单时 G1 未达成' 'TODO' "$(gate_field G1 state)"
expect 'TC-M3-01 无问题项时 G3 不适用' 'NA' "$(gate_field G3 state)"
expect 'TC-M3-01 无措施项时 G4 不适用' 'NA' "$(gate_field G4 state)"
expect 'TC-M3-01 无结果记录时 G5 未达成' 'TODO' "$(gate_field G5 state)"
expect 'TC-M3-01 无证据时 G6 未达成' 'TODO' "$(gate_field G6 state)"
expect 'TC-M3-01 未验收时 G9 未达成' 'TODO' "$(gate_field G9 state)"
expect 'TC-M3-01 地图为实时算式' 'LIVE' "$(jexpr "d['dataMode']")"
RATE_A=$(jexpr "d['completionRate']")
EXPECT_RATE=$(jexpr "int(sum(g['weight'] for g in d['gates'] if g['state']=='DONE')*100/sum(g['weight'] for g in d['gates'] if g['state']!='NA')+0.5)")
expect 'TC-M3-01 完成度等于达成权重除适用权重' "$EXPECT_RATE" "$RATE_A"

dispatch "$TASK_A"
call GET "/tasks/$TASK_A" "$T_ADMIN" >/dev/null
expect '派工后主责落库' "$EXEC_ID" "$(jexpr "d['task']['primaryAssigneeId']")"
call GET "/tasks/$TASK_A/completion-map" "$T_ADMIN" >/dev/null
expect 'TC-M3-01 派工但未写追踪时 G1 仍未达成' 'TODO' "$(gate_field G1 state)"

checkin "$TASK_A" 30 '现场确认毛刺批次并隔离，等备件到货' 'PROGRESS' \
  ',"blockerText":"备件未到货，卡在采购部"'
call GET "/tasks/$TASK_A/completion-map" "$T_ADMIN" >/dev/null
expect 'TC-M3-01 首条追踪后 G1 立即达成' 'DONE' "$(gate_field G1 state)"
expect 'TC-M3-01 登记阻碍后 G7 未达成' 'TODO' "$(gate_field G7 state)"
checkin "$TASK_A" 40 '备件已到货，恢复推进' 'PROGRESS' ',"resolvedBlocker":true'
call GET "/tasks/$TASK_A/completion-map" "$T_ADMIN" >/dev/null
expect 'TC-M3-01 解除阻碍后 G7 达成' 'DONE' "$(gate_field G7 state)"

# ---------------------------------------------------------------- M3-02
section 'M3-02 权重与条件集合按类型配置'
call GET '/tasks/217/completion-map' "$T_ADMIN" >/dev/null
expect_code 'TC-M3-02 巡查类工单地图可读' 0
expect 'TC-M3-02 巡查类 G8 被类型配置关闭为不适用' 'NA' "$(gate_field G8 state)"
expect 'TC-M3-02 关闭的条件给出排除说明' 'true' \
  "$(jexpr "'排除' in ((next((g for g in d['gates'] if g['code']=='G8'), None) or {}).get('hint') or '')")"
NA_COUNT=$(jexpr "sum(1 for g in d['gates'] if g['state']=='NA')")
call GET '/tasks/217/completion-map?includeNa=false' "$T_ADMIN" >/dev/null
expect 'TC-M3-02 includeNa=false 不返回不适用项' "$((9 - NA_COUNT))" "$(jexpr "len(d['gates'])")"
expect 'TC-M3-02 过滤后无 NA 行' '0' "$(jexpr "sum(1 for g in d['gates'] if g['state']=='NA')")"

ORIG_PATROL_G6=$(sql "select weight from gate_rule where task_type='PATROL' and gate_code='G6'")
sql "update gate_rule set weight=30 where task_type='PATROL' and gate_code='G6';"
call GET '/tasks/217/completion-map' "$T_ADMIN" >/dev/null
expect 'TC-M3-02 改配置后权重即时生效（20→30）' 30 "$(gate_field G6 weight)"
sql "update gate_rule set weight=$ORIG_PATROL_G6 where task_type='PATROL' and gate_code='G6';"
ORIG_PATROL_G6=''
call GET '/tasks/217/completion-map' "$T_ADMIN" >/dev/null
expect 'TC-M3-02 还原配置后权重回到 20' 20 "$(gate_field G6 weight)"

# ---------------------------------------------------------------- M3-03
section 'M3-03 退回后 G9 重新未达成'
checkin "$TASK_A" 60 '毛刺批次已返工复检合格，工装加装到位并留影像记录' 'RESULT'
call GET "/tasks/$TASK_A/completion-map" "$T_ADMIN" >/dev/null
expect 'TC-M3-03 结果类记录写入后 G5 达成' 'DONE' "$(gate_field G5 state)"
expect 'TC-M3-03 无附件时 G6 仍未达成' 'TODO' "$(gate_field G6 state)"
call POST "/tasks/$TASK_A/submit" "$T_ADMIN" \
  '{"resultSummary":"已按整改要求完成","criteriaChecks":[true,true]}' >/dev/null
expect_code 'TC-M3-03 提交验收成功' 0
call GET "/tasks/$TASK_A/completion-map" "$T_ADMIN" >/dev/null
expect 'TC-M3-03 已提交不等于 G9 达成' 'TODO' "$(gate_field G9 state)"
expect 'TC-M3-03 提交后 G9 提示等待验收' 'true' \
  "$(jexpr "'等待负责人通过' in ((next((g for g in d['gates'] if g['code']=='G9'), None) or {}).get('hint') or '')")"

call POST "/tasks/$TASK_A/verify" "$T_ADMIN" \
  '{"decision":"REJECT","rejectReason":"现场仍有毛刺未清理，整改前后对比记录缺失","criteriaChecks":[true,false]}' >/dev/null
expect_code 'TC-M3-03 验收退回收下理由' 0
call GET "/tasks/$TASK_A/completion-map" "$T_ADMIN" >/dev/null
G9_HINT=$(gate_field G9 hint)
expect 'TC-M3-03 退回后 G9 重新未达成' 'TODO' "$(gate_field G9 state)"
expect 'TC-M3-03 退回后提示带退回理由' 'true' \
  "$(jexpr "'验收被退回' in ((next((g for g in d['gates'] if g['code']=='G9'), None) or {}).get('hint') or '') and '毛刺未清理' in ((next((g for g in d['gates'] if g['code']=='G9'), None) or {}).get('hint') or '')")"
expect 'TC-M3-03 退回后工单回到 IN_PROGRESS' 'IN_PROGRESS' "$(jexpr "d['status']")"

# ---------------------------------------------------------------- M3-04
section 'M3-04 详情右栏动作无断点（接口契约部分）'
call GET "/tasks/$TASK_A/completion-map" "$T_ADMIN" >/dev/null
expect 'TC-M3-04 G1 动作指向派工区' "/tasks/$TASK_A#dispatch" "$(gate_field G1 actionUrl)"
expect 'TC-M3-04 G2 动作指向改期区' "/tasks/$TASK_A#reschedule" "$(gate_field G2 actionUrl)"
expect 'TC-M3-04 G3 动作指向问题区' "/tasks/$TASK_A#issues" "$(gate_field G3 actionUrl)"
expect 'TC-M3-04 G4 动作指向措施区' "/tasks/$TASK_A#actions" "$(gate_field G4 actionUrl)"
expect 'TC-M3-04 G5 动作指向追踪区' "/tasks/$TASK_A#checkin" "$(gate_field G5 actionUrl)"
expect 'TC-M3-04 G6 动作指向证据区' "/tasks/$TASK_A#checkin" "$(gate_field G6 actionUrl)"
expect 'TC-M3-04 G7 动作指向阻碍区' "/tasks/$TASK_A#blocker" "$(gate_field G7 actionUrl)"
expect 'TC-M3-04 G9 动作指向验收区' "/tasks/$TASK_A#verify" "$(gate_field G9 actionUrl)"

# ---------------------------------------------------------------- 造提醒与矩阵用单
section '准备：造 B/C/I 三张单（缺口提醒与 G4 缺人/缺期限）'
create_task "【M3验收】$SUF 滞留七日与问题未关" ',"issueList":["进场资料未齐，需补齐检验记录"]'
TASK_B=$NEW_ID
NO_B=$NEW_NO
dispatch "$TASK_B"
checkin "$TASK_B" 20 '资料收集完成一部分，检验记录待补' 'PROGRESS'
create_task "【M3验收】$SUF 滞留三日" ''
TASK_C=$NEW_ID
NO_C=$NEW_NO
dispatch "$TASK_C"
checkin "$TASK_C" 25 '已联系厂家索取报告，等待回复' 'PROGRESS'
create_task "【M3验收】$SUF 措施缺期限" ',"actionList":[{"content":"补充模具台账并归档"}]'
TASK_I=$NEW_ID
NO_I=$NEW_NO
call GET "/tasks/$TASK_I/completion-map" "$T_ADMIN" >/dev/null
expect 'TC-M3-01 措施缺期限时 G4 未达成' 'TODO' "$(gate_field G4 state)"
expect 'TC-M3-01 G4 提示点出缺期限' 'true' \
  "$(jexpr "'期限' in ((next((g for g in d['gates'] if g['code']=='G4'), None) or {}).get('hint') or '')")"

# ---------------------------------------------------------------- M3-05
section 'M3-05 部门缺口矩阵按条件排序'
call POST '/completion-map/snapshot' "$T_ADMIN" >/dev/null
expect_code 'TC-M3-05 当日快照可手动重算' 0
call GET '/completion-map/matrix?deptId=2&pageSize=200' "$T_ADMIN" >/dev/null
expect_code 'TC-M3-05 矩阵可读' 0
expect 'TC-M3-05 列覆盖 G1..G9' 'G1,G2,G3,G4,G5,G6,G7,G8,G9' \
  "$(jexpr "','.join(c['code'] for c in d['columns'])")"
expect 'TC-M3-05 每行九个格子' 'true' "$(jexpr "all(len(r['cells'])==9 for r in d['rows'])")"
expect 'TC-M3-05 未达成格子带滞留天数' 'true' \
  "$(jexpr "all(c['gapDays']>=1 for r in d['rows'] for c in r['cells'] if c['state']=='TODO')")"
expect 'TC-M3-05 逾期工单排在最前' 'true' "$(jexpr "bool(d['rows']) and d['rows'][0]['overdue']")"
expect 'TC-M3-05 逾期天数可读' 'true' "$(jexpr "d['rows'][0]['overdueDays']>0")"
expect 'TC-M3-05 列合计有积压' 'true' "$(jexpr "any(c['todoCount']>0 for c in d['columns'])")"
call GET '/completion-map/matrix?deptId=2&pageSize=200' "$T_EXEC" >/dev/null
expect 'TC-M3-05 执行者读矩阵被拦 TL-4031' 'TL-4031' "$(jget code)"

# ---------------------------------------------------------------- M3-06
section 'M3-06 矩阵快照陈旧标记'
sql "delete from task_gate_snapshot where snap_date='$TODAY'; delete from gate_gap_daily where snap_date='$TODAY';"
call POST "/completion-map/snapshot?date=$YDAY" "$T_ADMIN" >/dev/null
expect_code 'TC-M3-06 用昨日日期补跑快照' 0
call GET '/completion-map/matrix?deptId=2&pageSize=200' "$T_ADMIN" >/dev/null
expect 'TC-M3-06 快照日期停在上一日' "$YDAY" "$(jexpr "d['snapshotAt']")"
expect 'TC-M3-06 标记数据陈旧' 'true' "$(jexpr "d['stale']")"
call GET '/completion-map/gaps?deptId=2' "$T_ADMIN" >/dev/null
expect 'TC-M3-06 缺口排行同样标记陈旧' 'true' "$(jexpr "d['stale']")"
call POST '/completion-map/snapshot' "$T_ADMIN" >/dev/null
expect_code 'TC-M3-06 重跑当日快照恢复' 0
call GET '/completion-map/matrix?deptId=2&pageSize=200' "$T_ADMIN" >/dev/null
expect 'TC-M3-06 恢复后快照日期为当日' "$TODAY" "$(jexpr "d['snapshotAt']")"
expect 'TC-M3-06 恢复后不再陈旧' 'false' "$(jexpr "d['stale']")"

# ---------------------------------------------------------------- M3-08
section 'M3-08 缺口聚合与滞留排行'
call GET '/completion-map/matrix?deptId=2&pageSize=200' "$T_ADMIN" >/dev/null
MATRIX_TODO=$(jexpr "sum(c['todoCount'] for c in d['columns'])")
call GET '/completion-map/gaps?deptId=2' "$T_ADMIN" >/dev/null
expect_code 'TC-M3-08 缺口排行可读' 0
TOTAL_TODO=$(jexpr "d['totalTodo']")
expect 'TC-M3-08 缺口总数与矩阵列合计一致' "$MATRIX_TODO" "$TOTAL_TODO"
expect 'TC-M3-08 按条件聚合覆盖 G1..G9' 'G1,G2,G3,G4,G5,G6,G7,G8,G9' \
  "$(jexpr "','.join(g['code'] for g in d['byGate'])")"
expect 'TC-M3-08 按条件合计等于总数' "$TOTAL_TODO" "$(jexpr "sum(g['todoCount'] for g in d['byGate'])")"
expect 'TC-M3-08 按承担者合计等于总数' "$TOTAL_TODO" "$(jexpr "sum(h['todoCount'] for h in d['byHolder'])")"
expect 'TC-M3-08 滞留排行按天数降序' 'true' \
  "$(jexpr "all(d['top'][i]['gapDays']>=d['top'][i+1]['gapDays'] for i in range(len(d['top'])-1))")"
expect 'TC-M3-08 排行条目可追溯工单与条件' 'true' \
  "$(jexpr "all(t['taskNo'] and t['gateName'] for t in d['top'])")"

# ---------------------------------------------------------------- M3-09
section 'M3-09 三级提醒实际触发（三日、七日）'
sql "update task_gate_snapshot set state='TODO', gap_days=6 where snap_date='$YDAY' and task_id=$TASK_B and gate_code='G5';"
sql "update task_gate_snapshot set state='TODO', gap_days=2 where snap_date='$YDAY' and task_id=$TASK_C and gate_code='G5';"
sql "delete from notification where task_id in ($TASK_B,$TASK_C);"
call POST '/completion-map/snapshot' "$T_ADMIN" >/dev/null
expect_code 'TC-M3-09 快照跑完' 0
SENT=$(jexpr "d['notificationCount']")
expect 'TC-M3-09 只发两条超阈值缺口的三个提醒' 3 "$SENT"

call GET '/notifications?unreadOnly=false&limit=100' "$T_EXEC" >/dev/null
expect 'TC-M3-09 三日缺口只提醒承担者' '1' "$(notif_count "$NO_C" 'G5')"
expect 'TC-M3-09 承担者收到 FIRST 级' 'FIRST' "$(notif_field "$NO_C" 'G5' 'level')"
expect 'TC-M3-09 FIRST 级提示为滞留三日' '滞留三日' "$(notif_field "$NO_C" 'G5' 'levelLabel')"
expect 'TC-M3-09 FIRST 级滞留天数为 3' 3 "$(notif_field "$NO_C" 'G5' 'gapDays')"
expect 'TC-M3-09 七日缺口承担者也收到' 'SECOND' "$(notif_field "$NO_B" 'G5' 'level')"
expect 'TC-M3-09 SECOND 级滞留天数为 7' 7 "$(notif_field "$NO_B" 'G5' 'gapDays')"
expect 'TC-M3-09 提醒内容可追溯到工单与条件' 'true' \
  "$(jexpr "all(x in (next((n for n in d['records'] if n['taskNo']=='$NO_B' and n['gateCode']=='G5'), None) or {}).get('content','') for x in ('$NO_B','结果留痕'))")"

call GET '/notifications?unreadOnly=false&limit=100' "$T_ADMIN" >/dev/null
expect 'TC-M3-09 七日缺口同步提醒负责人' '1' "$(notif_count "$NO_B" 'G5')"
expect 'TC-M3-09 负责人收到 SECOND 级' 'SECOND' "$(notif_field "$NO_B" 'G5' 'level')"
expect 'TC-M3-09 负责人文案点明请跟进' 'true' \
  "$(jexpr "'请负责人跟进' in ((next((n for n in d['records'] if n['taskNo']=='$NO_B' and n['gateCode']=='G5'), None) or {}).get('content') or '')")"
expect 'TC-M3-09 三日缺口不扩散给负责人' 0 "$(notif_count "$NO_C")"

call POST '/completion-map/snapshot' "$T_ADMIN" >/dev/null
expect 'TC-M3-09 同日复跑不重复发（幂等）' 0 "$(jexpr "d['notificationCount']")"

call GET '/notifications?unreadOnly=true&limit=100' "$T_EXEC" >/dev/null
NOTIFY_ID=$(jexpr "(next((n['id'] for n in d['records'] if n['taskNo']=='$NO_C'), 0))")
call POST "/notifications/$NOTIFY_ID/read" "$T_EXEC" >/dev/null
expect_code '通知已读接口成功' 0
call GET '/notifications?unreadOnly=true&limit=100' "$T_EXEC" >/dev/null
expect '已读后不再出现在未读列表' 0 "$(notif_count "$NO_C")"

# ---------------------------------------------------------------- M3-07
section 'M3-07 事项完成地图的达成顺序'
create_task "【M3验收】$SUF 事项主单" ''
MATTER_P=$NEW_ID
create_task "【M3验收】$SUF 事项子单" ''
MATTER_C=$NEW_ID
create_task "【M3验收】$SUF 事项复发单" ''
MATTER_R=$NEW_ID
sql "update task set parent_id=$MATTER_P where id=$MATTER_C; update task set recurrence_of=$MATTER_P where id=$MATTER_R;"
call GET "/completion-map/matter/$MATTER_P" "$T_ADMIN" >/dev/null
expect_code 'TC-M3-07 事项地图可读' 0
expect 'TC-M3-07 父子与复发链合成一张事项' 3 "$(jexpr "len(d['tasks'])")"
expect 'TC-M3-07 链上包含三张单' 'true' \
  "$(jexpr "sorted(t['taskId'] for t in d['tasks']) == sorted([$MATTER_P,$MATTER_C,$MATTER_R])")"
expect 'TC-M3-07 按创建时间给出时间轴' 'true' \
  "$(jexpr "all(d['tasks'][i]['createdAt'] <= d['tasks'][i+1]['createdAt'] for i in range(len(d['tasks'])-1))")"
expect 'TC-M3-07 每张单都有九条条件' 'true' "$(jexpr "all(len(t['gates'])==9 for t in d['tasks'])")"
expect 'TC-M3-07 高亮反复停下的条件' 'true' \
  "$(jexpr "bool(d['stuckGateCode']) and d['stuckCount'] >= 2")"
call GET "/completion-map/matter/$MATTER_C" "$T_ADMIN" >/dev/null
expect 'TC-M3-07 从子单进入看到同一张事项' 3 "$(jexpr "len(d['tasks'])")"

# ---------------------------------------------------------------- M3-10
section 'M3-10 快照任务性能与保留策略'
expect 'TC-M3-10 保留天数配置为 180' 180 "$(sql "select cfg_value from sys_config where cfg_key='gate.snapshot.retention.days'")"
sql "insert into task_gate_snapshot (snap_date, task_id, dept_id, task_type, gate_code, state, gap_days, holder_id)
     values ('$OLD_DATE', 1, 2, 'QUALITY', 'G5', 'TODO', 120, 2);"
expect 'TC-M3-10 造出一条超期快照' 1 "$(sql "select count(*) from task_gate_snapshot where snap_date='$OLD_DATE'")"

if [ "$LOAD_TASKS" -gt 0 ]; then
  python3 - "$LOAD_TASKS" "$TMP/load.sql" <<'PY'
import sys
n = int(sys.argv[1])
out = sys.argv[2]
rows = []
for i in range(1, n + 1):
    rows.append("('M3L-%04d','【M3验收】压测工单 %d','QUALITY','MANUAL',2,1,2,2,'PENDING',"
                "DATE_ADD(NOW(), INTERVAL 30 DAY),0,1)" % (i, i))
with open(out, 'w') as f:
    f.write("INSERT INTO task (task_no,title,type,source_type,dept_id,owner_id,primary_assignee_id,"
            "priority,status,plan_end,progress,created_by) VALUES\n")
    f.write(",\n".join(rows))
    f.write(";\n")
PY
  sql_file "$TMP/load.sql"
  LOADED=$(sql "select count(*) from task where task_no like 'M3L-%'")
  expect "压测工单建 ${LOAD_TASKS} 张" "$LOAD_TASKS" "$LOADED"
else
  expect '跳过压测造数' 1 1
fi

call POST '/completion-map/snapshot' "$T_ADMIN" >/dev/null
expect_code 'TC-M3-10 压测快照跑完' 0
TASK_CNT=$(jexpr "d['taskCount']")
CELL_CNT=$(jexpr "d['cellCount']")
PRUNED=$(jexpr "d['prunedDays']")
DURATION=$(jexpr "d['durationMs']")
expect 'TC-M3-10 每张在办单九个条件格子' "$((TASK_CNT * 9))" "$CELL_CNT"
if [ "$DURATION" -lt 7200000 ]; then
  pass "TC-M3-10 快照在 ${DURATION} ms 内跑完 ${TASK_CNT} 张单（窗口两小时）"
else
  fail 'TC-M3-10 快照在两小时窗口内跑完' "实际 ${DURATION} ms"
fi
expect 'TC-M3-10 超过保留期的截面被裁剪' 'true' "$(python3 -c "print('true' if $PRUNED >= 1 else 'false')")"
expect 'TC-M3-10 裁剪后旧截面不再保留明细' 0 "$(sql "select count(*) from task_gate_snapshot where snap_date='$OLD_DATE'")"
expect 'TC-M3-10 裁剪后仍留按日按部门按条件聚合' 'true' \
  "$(python3 -c "print('true' if $(sql "select count(*) from gate_gap_daily where snap_date='$OLD_DATE'") >= 1 else 'false')")"
expect 'TC-M3-10 当日明细行数与返回格子数一致' "$CELL_CNT" \
  "$(sql "select count(*) from task_gate_snapshot where snap_date='$TODAY'")"
expect 'TC-M3-10 当日聚合覆盖部门与条件' 'true' \
  "$(python3 -c "print('true' if $(sql "select count(*) from gate_gap_daily where snap_date='$TODAY'") >= 9 else 'false')")"

cleanup_load
call POST '/completion-map/snapshot' "$T_ADMIN" >/dev/null
CLEAN_TASK_CNT=$(jexpr "d['taskCount']")
printf '  压测后清理：在办单 %s 张，快照恢复用时 %s ms\n' "$CLEAN_TASK_CNT" "$(jexpr "d['durationMs']")"

# ---------------------------------------------------------------- 汇总
section '汇总'
printf '通过 %d 项，失败 %d 项\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
  printf '\n失败明细：\n'
  for item in "${FAIL_LIST[@]}"; do printf '  - %s\n' "$item"; done
  rm -rf "$TMP"
  exit 1
fi
rm -rf "$TMP"
exit 0
