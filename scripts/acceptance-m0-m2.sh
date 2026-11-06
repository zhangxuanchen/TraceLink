#!/usr/bin/env bash
# TraceLink M0–M2 验收入口。对应用例见 docs/TEST-CASES.md。
# 用法：bash scripts/acceptance-m0-m2.sh
# 前置：后端已启动在 8090、MySQL 已迁移并灌入种子数据。
set -uo pipefail

B=${B:-http://127.0.0.1:8090/api/v1}
TMP=$(mktemp -d)
PASS=0
FAIL=0
FAIL_LIST=()
SUF=$(date +%H%M%S)
RANGE_FROM=2026-10-25
RANGE_TO=2026-10-31

pass() { PASS=$((PASS + 1)); printf '  \033[32m✔\033[0m %s\n' "$1"; }
fail() { FAIL=$((FAIL + 1)); FAIL_LIST+=("$1 — $2"); printf '  \033[31m✘\033[0m %s：%s\n' "$1" "$2"; }
section() { printf '\n\033[1m%s\033[0m\n' "$1"; }

cleanup_previous() { # 只清本脚本上一轮留下的【验收】工单，种子单 1–5 不动
  command -v mysql >/dev/null 2>&1 || return 0
  local list
  list=$(mysql -N -h127.0.0.1 -P3306 -uroot -p'tracelink' tracelink \
    -e "select id from task where title like '【验收】%' and id > 5" 2>/dev/null | paste -sd, -)
  [ -z "$list" ] && return 0
  mysql -h127.0.0.1 -P3306 -uroot -p'tracelink' tracelink -e "
    delete from task_checkin where task_id in ($list);
    delete from task_issue where task_id in ($list);
    delete from task_action where task_id in ($list);
    delete from task_assignee where task_id in ($list);
    delete from audit_log where biz_type='TASK' and biz_id in ($list);
    delete from task where id in ($list);" >/dev/null 2>&1
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

jexpr() { # 对 data 求值，如 d['task']['status']；布尔统一输出小写
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

expect_http() { # 名称 期望码 实际码
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "HTTP 期望 $2，实际 $3"; fi
}

expect_code() { # 名称 期望业务码（成功为 0）
  local actual
  actual=$(jget code)
  expect "$1" "$2" "$actual"
}

login() { # 账号 -> token
  curl -s -X POST "$B/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"admin123\"}" \
    | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('token','') if d.get('code')=='0' else '')"
}

section '准备：登录与健康检查'
cleanup_previous
health=$(curl -s -o /dev/null -w '%{http_code}' "${B%/api/v1}/actuator/health")
expect_http '健康检查返回 200' 200 "$health"
T_ADMIN=$(login admin)
T_OWNER=$(login xuwei)
T_EXEC=$(login wangxb)
expect 'admin 取得令牌' 'non-empty' "$([ -n "$T_ADMIN" ] && echo non-empty || echo empty)"
expect 'xuwei 取得令牌' 'non-empty' "$([ -n "$T_OWNER" ] && echo non-empty || echo empty)"
expect 'wangxb 取得令牌' 'non-empty' "$([ -n "$T_EXEC" ] && echo non-empty || echo empty)"

# ---------------------------------------------------------------- M0
section 'M0 登录、组织与姓名解析（TC-M0-02 ~ M0-08）'

status=$(curl -s -o "$TMP/body.json" -w '%{http_code}' -X POST "$B/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}')
expect_http 'TC-M0-02 错误口令返回 401' 401 "$status"
expect_code 'TC-M0-02 错误口令业务码 TL-4011' 'TL-4011'

status=$(call GET '/tasks' '')
expect_http 'TC-M0-03 缺令牌返回 401' 401 "$status"
expect_code 'TC-M0-03 缺令牌业务码 TL-4011' 'TL-4011'

call GET '/depts/tree' "$T_ADMIN" >/dev/null
expect_code 'TC-M0-04 部门树读取成功' 0
expect 'TC-M0-04 根节点为总公司' '总公司' "$(jexpr "d[0]['name'] if d else ''")"
expect 'TC-M0-04 根节点物化路径为 /1/' '/1/' "$(jexpr "d[0]['path'] if d else ''")"
expect 'TC-M0-04 根节点直属子部门 7 个' 7 "$(jexpr "len(d[0]['children']) if d else -1")"

DEPT_ID=$(
  call POST '/depts' "$T_ADMIN" "{\"code\":\"TST$SUF\",\"name\":\"验收测试部$SUF\",\"parentId\":1}" >/dev/null
  jexpr "d['id']"
)
[ -n "$DEPT_ID" ] && pass 'TC-M0-05 新建测试部门' || fail 'TC-M0-05 新建测试部门' '未返回 id'
call PUT "/depts/$DEPT_ID" "$T_ADMIN" "{\"code\":\"TST$SUF\",\"name\":\"验收测试部改名$SUF\",\"parentId\":1}" >/dev/null
expect 'TC-M0-05 部门改名生效' "验收测试部改名$SUF" "$(jexpr "d['name']")"
call DELETE "/depts/$DEPT_ID" "$T_ADMIN" >/dev/null
expect_code 'TC-M0-05 空部门可删除' 0

POS_ID=$(
  call POST '/positions' "$T_ADMIN" "{\"deptId\":7,\"code\":\"TS$SUF\",\"name\":\"验收测试岗$SUF\",\"jobLevel\":3}" >/dev/null
  jexpr "d['id']"
)
[ -n "$POS_ID" ] && pass 'TC-M0-06 新建测试职位' || fail 'TC-M0-06 新建测试职位' '未返回 id'
call PUT "/positions/$POS_ID" "$T_ADMIN" "{\"deptId\":7,\"code\":\"TS$SUF\",\"name\":\"验收测试岗改名$SUF\",\"jobLevel\":3}" >/dev/null
expect 'TC-M0-06 职位改名生效' "验收测试岗改名$SUF" "$(jexpr "d['name']")"
call DELETE "/positions/$POS_ID" "$T_ADMIN" >/dev/null
expect_code 'TC-M0-06 职位可删除' 0

call GET '/employees/options' "$T_ADMIN" >/dev/null
expect_code 'TC-M0-07 人员选项可读' 0
count=$(jexpr "len(d)")
if [ "$count" -ge 16 ]; then pass "TC-M0-07 人员选项不少于 16 人（实际 ${count}）"; else fail 'TC-M0-07 人员选项不少于 16 人' "实际 ${count}"; fi

call GET '/employees/resolve?name=%E5%BC%A0%E5%8E%9A%E7%94%9F' "$T_ADMIN" >/dev/null
expect 'TC-M0-08 本名解析唯一命中' '张厚生' "$(jexpr "d[0]['name'] if d else ''")"
call GET '/employees/resolve?name=%E5%BC%A0%E6%80%BB' "$T_ADMIN" >/dev/null
expect 'TC-M0-08 别名「张总」解析到张厚生' '张厚生' "$(jexpr "d[0]['name'] if d else ''")"
call GET '/employees/resolve?name=%E6%9F%A5%E6%97%A0%E6%AD%A4%E4%BA%BA' "$T_ADMIN" >/dev/null
expect 'TC-M0-08 未命中返回空且不猜测' 0 "$(jexpr "len(d)")"

# ---------------------------------------------------------------- M1
section 'M1 工单与追踪（TC-M1-01 ~ M1-16）'

OWNER_ID=$(call GET '/employees/resolve?name=%E5%BC%A0%E5%8E%9A%E7%94%9F' "$T_ADMIN" >/dev/null; jexpr "d[0]['id']")
ASSIGNEE_ID=$(call GET '/employees/resolve?name=%E8%AE%B8%E4%BC%9F' "$T_ADMIN" >/dev/null; jexpr "d[0]['id']")
TITLE="【验收】$SUF 生产部整改闭环"
PLAN_END=$(date -v+7d '+%Y-%m-%dT18:00:00' 2>/dev/null || date -d '+7 day' '+%Y-%m-%dT18:00:00')

status=$(call POST '/tasks' "$T_ADMIN" "{
  \"title\":\"$TITLE\",\"type\":\"QUALITY\",\"deptId\":2,\"ownerId\":$OWNER_ID,
  \"primaryAssigneeId\":$ASSIGNEE_ID,\"priority\":1,\"planEnd\":\"$PLAN_END\",
  \"acceptCriteria\":[\"现场已恢复受控\",\"有整改前后对比记录\"],
  \"issueList\":[\"毛刺超标批次未隔离\"],
  \"actionList\":[{\"content\":\"加装毛刺清理工装\",\"ownerId\":$ASSIGNEE_ID,\"dueDate\":\"${PLAN_END%%T*}\"}]
}")
expect_http 'TC-M1-01 建单返回 201' 201 "$status"
expect_code 'TC-M1-01 建单成功' 0
expect 'TC-M1-01 新建工单状态为 PENDING' PENDING "$(jexpr "d['status']")"
TASK_ID=$(jexpr "d['taskId']")
TASK_NO=$(jexpr "d['taskNo']")
if [ -n "$TASK_ID" ]; then pass "TC-M1-01 工单编号 $TASK_NO"; else fail 'TC-M1-01 工单编号' '未返回 taskId'; fi

status=$(call POST '/tasks' "$T_ADMIN" "{
  \"title\":\"$TITLE\",\"type\":\"QUALITY\",\"deptId\":2,\"ownerId\":$OWNER_ID,
  \"primaryAssigneeId\":$ASSIGNEE_ID,\"planEnd\":\"$PLAN_END\"}")
expect_http 'TC-M1-02 同部门同标题第二次建单返回 409' 409 "$status"
expect_code 'TC-M1-02 业务码 TL-4093' 'TL-4093'

call GET '/tasks/2' "$T_OWNER" >/dev/null
expect 'TC-M1-03 负责人越部门读单被拦' 'TL-4032' "$(jget code)"

call POST "/tasks/$TASK_ID/dispatch" "$T_ADMIN" "{\"primaryAssigneeId\":$ASSIGNEE_ID,\"planEnd\":\"$PLAN_END\"}" >/dev/null
expect_code 'TC-M1-04 派工成功' 0
expect 'TC-M1-04 派工后主责落库' "$ASSIGNEE_ID" "$(jexpr "d['task']['primaryAssigneeId']")"

call POST "/tasks/$TASK_ID/checkin" "$T_OWNER" \
  '{"progress":30,"content":"已到场确认毛刺批次并隔离","resultType":"PROGRESS"}' >/dev/null
expect_code 'TC-M1-05 写追踪记录成功' 0
expect 'TC-M1-05 首条记录后进入 IN_PROGRESS' 'IN_PROGRESS' "$(jexpr "d['taskStatus']")"
expect 'TC-M1-05 无附件记录判为弱证据' 'WEAK' "$(jexpr "d['closureLevel']")"

call POST "/tasks/$TASK_ID/checkin" "$T_OWNER" \
  '{"progress":20,"content":"退回进度以测试拦截","resultType":"PROGRESS"}' >/dev/null
expect_code 'TC-M1-06 进度回退业务码 TL-4222' 'TL-4222'

call POST "/tasks/$TASK_ID/checkin" "$T_OWNER" \
  '{"progress":40,"content":"已整改","resultType":"RESULT"}' >/dev/null
expect_code 'TC-M1-06 结果类记录过短 TL-4223' 'TL-4223'

call POST "/tasks/$TASK_ID/checkin" "$T_OWNER" \
  '{"progress":40,"content":"施工方在等备件，本周无法完工","resultType":"PROGRESS","blockerText":"备件未到货，卡在采购部"}' >/dev/null
expect 'TC-M1-07 登记阻碍后状态 BLOCKED' 'BLOCKED' "$(jexpr "d['taskStatus']")"

call GET '/tasks/blocked' "$T_ADMIN" >/dev/null
expect 'TC-M1-07 阻碍看板命中该单' 'true' "$(jexpr "any(r['id']==$TASK_ID for r in d)")"

call POST "/tasks/$TASK_ID/checkin" "$T_OWNER" \
  '{"progress":50,"content":"备件已到货，恢复施工","resultType":"PROGRESS","resolvedBlocker":true}' >/dev/null
expect 'TC-M1-07 解除阻碍后回到 IN_PROGRESS' 'IN_PROGRESS' "$(jexpr "d['taskStatus']")"

call POST "/tasks/$TASK_ID/urge" "$T_ADMIN" >/dev/null
expect_code 'TC-M1-12 首次催办成功' 0
call POST "/tasks/$TASK_ID/urge" "$T_ADMIN" >/dev/null
expect_code 'TC-M1-12 同日二次催办 TL-4092' 'TL-4092'

call POST "/tasks/$TASK_ID/submit" "$T_OWNER" '{"resultSummary":"已按整改要求完成","criteriaChecks":[true,true]}' >/dev/null
expect_code 'TC-M1-08 无结果类记录提交被拦 TL-4221' 'TL-4221'

call POST "/tasks/$TASK_ID/checkin" "$T_OWNER" \
  '{"progress":70,"content":"毛刺批次已全部返工复检合格，工装已加装到位","resultType":"RESULT"}' >/dev/null
expect_code 'TC-M1-05 结果类记录写入成功' 0

call POST "/tasks/$TASK_ID/submit" "$T_OWNER" '{"resultSummary":"已按整改要求完成","criteriaChecks":[true,true]}' >/dev/null
expect 'TC-M1-08 条件判定齐全后提交成功' 'SUBMITTED' "$(jexpr "d['task']['status']")"

call POST "/tasks/$TASK_ID/verify" "$T_ADMIN" '{"decision":"PASS","criteriaChecks":[true,true]}' >/dev/null
expect_code 'TC-M1-09 通过但不填结果说明被拦 TL-4221' 'TL-4221'

call POST "/tasks/$TASK_ID/verify" "$T_ADMIN" '{"decision":"REJECT"}' >/dev/null
expect_code 'TC-M1-09 退回不填理由 TL-4224' 'TL-4224'

call POST "/tasks/$TASK_ID/verify" "$T_ADMIN" \
  '{"decision":"REJECT","rejectReason":"现场仍有毛刺未清理，整改前后对比记录缺失","criteriaChecks":[true,false]}' >/dev/null
expect 'TC-M1-09 退回后回到 IN_PROGRESS' 'IN_PROGRESS' "$(jexpr "d['taskStatus']")"
expect 'TC-M1-09 退回次数记 1' 1 "$(jexpr "d['rejectCount']")"

call POST "/tasks/$TASK_ID/submit" "$T_OWNER" '{"resultSummary":"已补对比记录并重新清理","criteriaChecks":[true,true]}' >/dev/null
expect 'TC-M1-09 再次提交成功' 'SUBMITTED' "$(jexpr "d['task']['status']")"

call POST "/tasks/$TASK_ID/verify" "$T_ADMIN" \
  '{"decision":"PASS","resultSummary":"复检合格，对比记录齐全","criteriaChecks":[true,true]}' >/dev/null
expect 'TC-M1-09 验收通过后状态 DONE' 'DONE' "$(jexpr "d['taskStatus']")"
expect 'TC-M1-09 退回次数保持 1' 1 "$(jexpr "d['rejectCount']")"

call GET "/tasks/$TASK_ID" "$T_ADMIN" >/dev/null
expect 'TC-M1-09 验收通过写入实际完成时间' 'non-empty' "$([ -n "$(jexpr "d['task']['actualEnd']")" ] && echo non-empty || echo empty)"
expect 'TC-M1-09 验收通过判为强证据' 'STRONG' "$(jexpr "d['task']['closureLevel']")"
expect 'TC-M1-09 通过后进度置 100' 100 "$(jexpr "d['task']['progress']")"

call POST "/tasks/$TASK_ID/cancel" "$T_ADMIN" '{"reason":"状态机负向用例"}' >/dev/null
expect_code 'TC-M1-14 已完成工单不可取消 TL-4091' 'TL-4091'

call POST "/tasks/$TASK_ID/checkin" "$T_OWNER" \
  '{"progress":100,"content":"工单已完成后补写记录","resultType":"PROGRESS"}' >/dev/null
expect_code 'TC-M1-10 已完成工单不能写追踪 TL-4091' 'TL-4091'

call GET "/tasks/$TASK_ID/timeline" "$T_ADMIN" >/dev/null
expect_code 'TC-M1-16 时间线可读' 0
expect 'TC-M1-16 时间线包含退回记录' 'true' "$(jexpr "any('验收退回' in (r.get('content') or '') for r in d)")"

# ---------------------------------------------------------------- M2
section 'M2 导入导出与统计（TC-M2-08 ~ M2-12）'

call GET "/stats/overview?from=$RANGE_FROM&to=$RANGE_TO" "$T_ADMIN" >/dev/null
expect_code 'TC-M2-12 口径指标接口成功' 0
rate=$(jexpr "next((k['value'] for k in d['kpis'] if k['code']=='completion_rate'), None)")
if python3 -c "import sys;sys.exit(0 if 0 <= float('$rate') <= 100 else 1)"; then
  pass "TC-M2-12 完成率在 0–100（实际 ${rate}）"
else
  fail 'TC-M2-12 完成率在 0–100' "实际 $rate"
fi
hours=$(jexpr "next((k['value'] for k in d['kpis'] if k['code']=='avg_hours'), None)")
if python3 -c "import sys;sys.exit(0 if float('$hours') >= 0 else 1)"; then
  pass "TC-M2-12 平均完成时长非负（实际 ${hours}）"
else
  fail 'TC-M2-12 平均完成时长非负' "实际 $hours"
fi

call GET "/stats/person?from=$RANGE_FROM&to=$RANGE_TO" "$T_ADMIN" >/dev/null
expect_code 'TC-M2-12 人员指标接口成功' 0
expect 'TC-M2-12 雷达六维不超过 100' 'true' "$(jexpr "all(x['value'] <= 100 for x in d['radar'])")"

call GET "/stats/overview?from=$RANGE_FROM&to=$RANGE_TO" "$T_EXEC" >/dev/null
expect_code 'TC-X-02 执行者读口径指标被拦 TL-4031' 'TL-4031'

LEDGER_ID=$(
  call POST '/exports' "$T_ADMIN" "{\"template\":\"LEDGER\",\"from\":\"$RANGE_FROM\",\"to\":\"$RANGE_TO\"}" >/dev/null
  jexpr "d['id']"
)
expect 'TC-M2-08 台账行数不为 1' 'true' "$(jexpr "d['rowCnt'] > 1")"
DAILY_ID=$(
  call POST '/exports' "$T_ADMIN" "{\"template\":\"DAILY\",\"from\":\"$RANGE_FROM\",\"to\":\"$RANGE_TO\"}" >/dev/null
  jexpr "d['id']"
)
expect 'TC-M2-08 日报文件名无哈希前缀' 'true' "$(jexpr "'日报回填' in d['fileName']")"

curl -s -o "$TMP/daily.xlsx" -H "Authorization: Bearer $T_ADMIN" "$B/exports/$DAILY_ID/download"
expect 'TC-M2-08 日报可下载' 'non-empty' "$([ -s "$TMP/daily.xlsx" ] && echo non-empty || echo empty)"

BEFORE_TOTAL=$(call GET '/tasks?pageSize=1' "$T_ADMIN" >/dev/null; jexpr "d['total']")

BATCH_ID=$(curl -s -X POST "$B/imports" -H "Authorization: Bearer $T_ADMIN" \
  -F "file=@$TMP/daily.xlsx" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('id',''))")
[ -n "$BATCH_ID" ] && pass "TC-M2-09 回导件上传成功（批次 ${BATCH_ID}）" || fail 'TC-M2-09 回导件上传' '未返回批次 id'

curl -s -o "$TMP/body.json" -H "Authorization: Bearer $T_ADMIN" "$B/imports/$BATCH_ID/preview"
expect_code 'TC-M2-09 回导预览成功' 0
expect 'TC-M2-09 回导无新建动作' 0 "$(jexpr "sum(1 for r in d['rows'] if r['action']=='CREATE')")"
expect 'TC-M2-09 回导行识别率 100%' 'true' "$(jexpr "d['recognition']['rowRate'] >= 0.999")"

call POST "/imports/$BATCH_ID/confirm" "$T_ADMIN" >/dev/null
expect_code 'TC-M2-09 回导确认成功' 0
expect 'TC-M2-09 回导新增 0 张' 0 "$(jexpr "d['created']")"
expect 'TC-M2-09 回导失败 0 行' 0 "$(jexpr "d['failed']")"

AFTER_TOTAL=$(call GET '/tasks?pageSize=1' "$T_ADMIN" >/dev/null; jexpr "d['total']")
expect 'TC-M2-09 回导后工单总数不变' "$BEFORE_TOTAL" "$AFTER_TOTAL"

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
