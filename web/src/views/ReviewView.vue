<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">复盘与建议</h2>
        <p class="page-sub">评价结论带证据可下钻，建议进待确认队列；采纳后才由系统执行建单、改期、催办等确定性动作</p>
      </div>
      <div class="head-actions">
        <button class="btn" :disabled="loading" @click="refreshAll">{{ loading ? '加载中…' : '刷新' }}</button>
      </div>
    </div>

    <div v-if="!canReview" class="form-error">当前账号没有评价与建议的查看权限，请使用管理者或负责人员工登录。</div>

    <template v-else>
      <div v-if="pageError" class="form-error">{{ pageError }}</div>
      <div v-if="notice" class="notice">
        <span class="tag tag-ok">完成</span>
        <span>{{ notice }}</span>
        <router-link v-if="noticeTaskId" :to="`/tasks/${noticeTaskId}`" class="btn btn-link">打开工单</router-link>
        <button class="btn-link" @click="clearNotice">关闭</button>
      </div>

      <!-- 评价：只对管理者开放 -->
      <div v-if="isManager" class="card block">
        <div class="block-head">
          <div>
            <h3>人员与任务评价</h3>
            <p class="muted">人员六维复用统计口径；任务四维输出复盘卡。周期唯一，重复触发返回首次结果。</p>
          </div>
        </div>
        <div class="toolbar">
          <input v-model="evalPeriod" type="month" style="width: 150px" />
          <select v-model="evalTarget" style="width: 132px">
            <option value="">人员与任务</option>
            <option v-for="t in EVAL_TARGETS" :key="t.code" :value="t.code">{{ t.label }}</option>
          </select>
          <button class="btn" :disabled="evalLoading" @click="loadEvaluations">查询</button>
          <button class="btn btn-primary" :disabled="evalRunning" @click="runEvaluations">
            {{ evalRunning ? '生成中…' : '生成评价' }}
          </button>
          <span class="spacer"></span>
          <span class="muted small">共 {{ evaluations.length }} 条</span>
        </div>
        <div v-if="evalRunResult" class="run-result">
          <span class="tag tag-ok">新建人员 {{ evalRunResult.employeeCreated }}</span>
          <span class="tag tag-ok">新建任务 {{ evalRunResult.taskCreated }}</span>
          <span v-if="evalRunResult.skipped" class="tag tag-warn">跳过 {{ evalRunResult.skipped }}</span>
          <span class="muted small">{{ evalRunResult.from }} ~ {{ evalRunResult.to }}</span>
          <span v-for="(n, i) in evalRunResult.notes || []" :key="i" class="muted small">· {{ n }}</span>
        </div>
        <div v-if="evalError" class="form-error">{{ evalError }}</div>

        <table class="table">
          <thead>
            <tr>
              <th>对象</th>
              <th style="width: 92px">类型</th>
              <th style="width: 96px">周期</th>
              <th style="width: 76px" class="num">综合分</th>
              <th style="width: 110px">置信度 / 样本</th>
              <th style="width: 96px">状态</th>
              <th style="width: 136px">生成时间</th>
              <th style="width: 148px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in evaluations" :key="e.id">
              <td>
                <div class="target-name">{{ e.targetName || '—' }}</div>
                <div class="muted small">#{{ e.targetId }}</div>
              </td>
              <td><span class="tag">{{ e.targetTypeLabel }}</span></td>
              <td class="num">{{ e.period }}</td>
              <td class="num">{{ scoreText(e.score) }}</td>
              <td class="num small">{{ percent(e.confidence) }} / {{ e.sampleCnt ?? 0 }}</td>
              <td><span class="tag" :class="evalStatusClass(e.status)">{{ e.statusLabel }}</span></td>
              <td class="num small">{{ fmtDateTime(e.createdAt) }}</td>
              <td>
                <button class="btn-link" @click="openEval(e)">证据</button>
                <button v-if="e.status !== 'APPEALED'" class="btn-link appeal-link"
                        @click="openAppeal(e)">申诉</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!evaluations.length && !evalLoading" class="empty">
          该周期还没有评价，点「生成评价」按工单与追踪记录算一次
        </div>
      </div>

      <!-- 建议：管理者与负责人都能看，生成只对管理者开放 -->
      <div class="card block">
        <div class="block-head">
          <div>
            <h3>待确认建议</h3>
            <p class="muted">
              五类建议只读业务数据，采纳时由应用服务执行确定性动作；未采纳的建议不产生任何数据变化。
            </p>
          </div>
        </div>
        <div class="toolbar">
          <select v-model="sugType" style="width: 160px">
            <option value="">全部类型</option>
            <option v-for="t in SUGGESTION_TYPES" :key="t.code" :value="t.code">{{ t.label }}</option>
          </select>
          <select v-model="sugStatus" style="width: 120px">
            <option value="">全部状态</option>
            <option v-for="s in SUGGESTION_STATUSES" :key="s.code" :value="s.code">{{ s.label }}</option>
          </select>
          <select v-model="sugTarget" style="width: 110px">
            <option value="">全部对象</option>
            <option v-for="t in SUGGESTION_TARGETS" :key="t.code" :value="t.code">{{ t.label }}</option>
          </select>
          <button class="btn" :disabled="sugLoading" @click="loadSuggestions">应用</button>
          <button v-if="isManager" class="btn btn-primary" :disabled="sugRunning" @click="runSuggestions">
            {{ sugRunning ? '生成中…' : (sugType ? `生成${suggestionTypeMeta(sugType).label}` : '生成建议') }}
          </button>
          <span class="spacer"></span>
          <span class="muted small">共 {{ suggestions.length }} 条</span>
        </div>
        <div v-if="sugRunResult" class="run-result">
          <span class="tag tag-ok">新增 {{ sugRunResult.created }}</span>
          <span v-if="sugRunResult.skipped" class="tag tag-warn">跳过 {{ sugRunResult.skipped }}</span>
          <span v-for="(n, i) in sugRunResult.notes || []" :key="i" class="muted small">· {{ n }}</span>
        </div>
        <div v-if="sugError" class="form-error">{{ sugError }}</div>

        <div class="sug-list">
          <div v-for="s in suggestions" :key="s.id" class="sug" :class="{ done: s.status !== 'PENDING' }">
            <div class="sug-head">
              <span class="tag sug-type">{{ s.typeLabel }}</span>
              <span class="tag" :class="suggestionStatusClass(s.status)">{{ s.statusLabel }}</span>
              <span class="sug-target">{{ s.targetName || '—' }}</span>
              <span class="spacer"></span>
              <span class="muted num small">{{ fmtDateTime(s.createdAt) }}</span>
            </div>
            <div class="sug-summary">{{ summaryOf(s) }}</div>
            <div class="sug-foot">
              <span class="muted small">
                {{ s.modelName }} · {{ s.promptVer }} · {{ (s.tokenIn || 0) + (s.tokenOut || 0) }} tokens
                <span v-if="s.latencyMs"> · {{ s.latencyMs }} ms</span>
              </span>
              <span class="spacer"></span>
              <button class="btn-link" @click="openSuggestion(s)">详情与依据</button>
              <button v-if="s.status === 'PENDING'" class="btn" :disabled="acceptingId === s.id"
                      @click="acceptSuggestion(s)">
                {{ acceptingId === s.id ? '采纳中…' : '采纳' }}
              </button>
              <span v-else-if="s.acceptedByName" class="muted small">
                {{ s.acceptedByName }} 于 {{ fmtDateTime(s.acceptedAt) }} 采纳
              </span>
            </div>
          </div>
        </div>
        <div v-if="!suggestions.length && !sugLoading" class="empty">
          {{ isManager ? '还没有建议，点「生成建议」扫描在办工单' : '当前范围内没有待确认建议' }}
        </div>
      </div>
    </template>

    <!-- 评价证据 -->
    <BaseModal v-if="evalOpen" :title="evalDetail ? `评价：${evalDetail.targetName || ''}` : '评价证据'"
               @close="evalOpen = false">
      <div v-if="evalDetailLoading" class="empty">加载中…</div>
      <div v-if="evalDetailError" class="form-error">{{ evalDetailError }}</div>
      <template v-if="evalDetail">
        <div class="eval-top">
          <div class="eval-score">
            <span class="num">{{ scoreText(evalDetail.score) }}</span>
            <span class="muted small">综合分</span>
          </div>
          <div class="eval-meta">
            <div><span class="tag">{{ evalDetail.targetTypeLabel }}</span> <span class="muted">{{ evalDetail.period }}</span></div>
            <div class="muted small">
              置信度 {{ percent(evalDetail.confidence) }} · 样本 {{ evalDetail.sampleCnt ?? 0 }} · {{ evalDetail.statusLabel }}
            </div>
            <div v-if="evalDetail.status === 'APPEALED'" class="muted small appeal-reason">
              申诉理由：{{ appealReasonOf(evalDetail) }}
            </div>
          </div>
        </div>

        <div class="scores">
          <div v-for="dim in dimensionsOf(evalDetail)" :key="dim.code" class="score-row">
            <div class="score-label">
              <span>{{ dim.label }}</span>
              <span class="muted small">{{ dim.desc }}</span>
            </div>
            <div class="bar">
              <div class="bar-fill" :style="{ width: `${barWidth(evalDetail.scores?.[dim.code])}%` }"></div>
            </div>
            <span class="num score-value">{{ scoreText(evalDetail.scores?.[dim.code]) }}</span>
            <span class="muted num weight">{{ weightText(evalDetail.scores, dim.code) }}</span>
          </div>
          <p v-if="evalDetail.scores?.note" class="muted small">{{ evalDetail.scores.note }}</p>
        </div>

        <div class="evidence">
          <div class="evidence-title">证据（点击工单可下钻）</div>
          <div v-for="row in evidenceRows(evalDetail)" :key="row.label" class="evidence-row">
            <span class="evidence-label">{{ row.label }}</span>
            <span class="evidence-value">
              <router-link v-for="link in row.links || []" :key="link.id" :to="`/tasks/${link.id}`" class="btn-link">
                {{ link.text }}
              </router-link>
              <span v-if="row.value">{{ row.value }}</span>
            </span>
          </div>
        </div>
      </template>
      <template #footer>
        <button v-if="evalDetail && evalDetail.status !== 'APPEALED'" class="btn" @click="openAppeal(evalDetail)">申诉</button>
        <button class="btn" @click="evalOpen = false">关闭</button>
      </template>
    </BaseModal>

    <!-- 申诉 -->
    <BaseModal v-if="appealOpen" title="发起评价申诉" @close="appealOpen = false">
      <p class="muted small appeal-tip">
        申诉只改评价状态并记录理由，分数与证据不变；理由至少 5 个字，便于复核时对齐口径。
      </p>
      <div v-if="appealTarget" class="appeal-target">
        <span class="tag">{{ appealTarget.targetTypeLabel }}</span>
        <span>{{ appealTarget.targetName || '—' }}</span>
        <span class="muted num">{{ appealTarget.period }}</span>
      </div>
      <div class="field">
        <label>申诉理由<span class="req">*</span></label>
        <textarea v-model="appealReason" rows="4" placeholder="例如：本周期有 3 张工单属于跨部门协办，完成率口径与统计口径不一致"></textarea>
      </div>
      <div v-if="appealError" class="form-error">{{ appealError }}</div>
      <template #footer>
        <button class="btn" @click="appealOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="appealSubmitting" @click="submitAppeal">
          {{ appealSubmitting ? '提交中…' : '提交申诉' }}
        </button>
      </template>
    </BaseModal>

    <!-- 建议详情 -->
    <BaseModal v-if="sugOpen" :title="sugDetail ? sugDetail.typeLabel : '建议详情'" @close="sugOpen = false">
      <div v-if="sugDetailLoading" class="empty">加载中…</div>
      <div v-if="sugDetailError" class="form-error">{{ sugDetailError }}</div>
      <template v-if="sugDetail">
        <div class="sug-detail-head">
          <span class="tag">{{ sugDetail.targetTypeLabel }}</span>
          <span>{{ sugDetail.targetName || '—' }}</span>
          <span class="tag" :class="suggestionStatusClass(sugDetail.status)">{{ sugDetail.statusLabel }}</span>
        </div>
        <p class="sug-detail-summary">{{ summaryOf(sugDetail) }}</p>

        <div v-if="sugDetail.type === 'DISPATCH'" class="detail-section">
          <div class="detail-title">候选执行人（按在办负载升序、历史成功率降序）</div>
          <table class="table compact">
            <thead>
              <tr><th>姓名</th><th>部门</th><th class="num">在办</th><th class="num">完成</th><th class="num">成功率</th></tr>
            </thead>
            <tbody>
              <tr v-for="c in sugDetail.payload?.candidates || []" :key="c.employeeId">
                <td>{{ c.name }}</td>
                <td>{{ c.deptName || '—' }}</td>
                <td class="num">{{ c.openCount }}</td>
                <td class="num">{{ c.doneCount }}</td>
                <td class="num">{{ percent(c.successRate) }}%</td>
              </tr>
            </tbody>
          </table>
          <p v-if="sugDetail.payload?.dispatch" class="muted small">
            采纳动作：指派 {{ assigneeName(sugDetail.payload.dispatch.primaryAssigneeId) }}，优先级
            {{ priorityLabel(sugDetail.payload.dispatch.priority) }}
          </p>
        </div>

        <div v-if="sugDetail.type === 'ACTION'" class="detail-section">
          <div class="detail-title">将新建的复盘单（采纳后走 TSK-1）</div>
          <div class="detail-grid">
            <div><span class="muted">标题</span><span>{{ sugDetail.payload?.create?.title }}</span></div>
            <div><span class="muted">类型</span><span>{{ typeLabel(sugDetail.payload?.create?.type) }}</span></div>
            <div><span class="muted">计划完成</span><span class="num">{{ sugDetail.payload?.create?.planEnd }}</span></div>
            <div><span class="muted">历史平均耗时</span><span class="num">{{ hoursText(sugDetail.payload?.historyAvgHours) }}</span></div>
          </div>
          <div class="detail-title">召回的历史措施</div>
          <ul class="measures">
            <li v-for="(m, i) in sugDetail.payload?.historyMeasures || []" :key="i">{{ m }}</li>
            <li v-if="!(sugDetail.payload?.historyMeasures || []).length" class="muted">暂无历史措施记录，采纳后需补充</li>
          </ul>
        </div>

        <div v-if="sugDetail.type === 'DUE_DATE'" class="detail-section">
          <div class="detail-title">期限调整</div>
          <div class="detail-grid">
            <div><span class="muted">原计划</span><span class="num">{{ sugDetail.payload?.proposal?.oldPlanEnd || '空' }}</span></div>
            <div><span class="muted">建议期限</span><span class="num">{{ sugDetail.payload?.proposal?.newPlanEnd }}</span></div>
            <div><span class="muted">样本量</span><span class="num">{{ sugDetail.payload?.stats?.sample }}</span></div>
            <div><span class="muted">中位数 / 八成位</span><span class="num">
              {{ hoursText(sugDetail.payload?.stats?.medianHours) }} / {{ hoursText(sugDetail.payload?.stats?.p80Hours) }}
            </span></div>
          </div>
          <p class="muted small">{{ sugDetail.payload?.proposal?.reasonText }}</p>
          <p class="muted small">根因：{{ sugDetail.payload?.proposal?.rootCause }}</p>
          <p class="muted small">措施：{{ sugDetail.payload?.proposal?.measure }}</p>
        </div>

        <div v-if="sugDetail.type === 'ESCALATE'" class="detail-section">
          <div class="detail-title">升级对象</div>
          <div class="detail-grid">
            <div><span class="muted">姓名</span><span>{{ sugDetail.payload?.escalate?.name }}</span></div>
            <div><span class="muted">关系</span><span>{{ sugDetail.payload?.escalate?.relation }}</span></div>
            <div><span class="muted">采纳动作</span><span>{{ sugDetail.payload?.escalate?.note }}</span></div>
          </div>
        </div>

        <div v-if="sugDetail.type === 'POLICY_GAP'" class="detail-section">
          <div class="detail-title">近 30 天同类事项</div>
          <table class="table compact">
            <thead>
              <tr><th>工单号</th><th>标题</th><th>问题摘要</th><th>创建时间</th></tr>
            </thead>
            <tbody>
              <tr v-for="o in sugDetail.payload?.occurrences || []" :key="o.taskId">
                <td class="num">{{ o.taskNo }}</td>
                <td>{{ o.title }}</td>
                <td class="muted small">{{ o.issue || '—' }}</td>
                <td class="num small">{{ o.createdAt }}</td>
              </tr>
            </tbody>
          </table>
          <p class="muted small">建议动作：{{ sugDetail.payload?.proposedAction }}</p>
        </div>

        <div class="detail-section">
          <div class="detail-title">证据与成本</div>
          <div class="detail-grid">
            <div v-for="row in suggestionEvidenceRows(sugDetail)" :key="row.label">
              <span class="muted">{{ row.label }}</span><span class="num">{{ row.value }}</span>
            </div>
            <div><span class="muted">模型</span><span>{{ sugDetail.modelName }} · {{ sugDetail.promptVer }}</span></div>
            <div><span class="muted">tokens</span><span class="num">{{ (sugDetail.tokenIn || 0) + (sugDetail.tokenOut || 0) }}</span></div>
            <div><span class="muted">耗时</span><span class="num">{{ sugDetail.latencyMs }} ms</span></div>
          </div>
        </div>

        <div v-if="sugDetail.status === 'ACCEPTED'" class="detail-section accept-result">
          <div class="detail-title">采纳结果</div>
          <div class="muted small">
            采纳人 {{ sugDetail.acceptedByName || '—' }} · {{ fmtDateTime(sugDetail.acceptedAt) }}
          </div>
          <div class="num small accept-raw">{{ sugDetail.acceptResult || '—' }}</div>
        </div>
      </template>
      <template #footer>
        <button class="btn" @click="sugOpen = false">关闭</button>
        <button v-if="sugDetail && sugDetail.status === 'PENDING'" class="btn btn-primary"
                :disabled="acceptingId === sugDetail.id" @click="acceptSuggestion(sugDetail)">
          {{ acceptingId === sugDetail.id ? '采纳中…' : '采纳并执行' }}
        </button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { USER_KEY } from '../api/http.js'
import {
  currentMonth,
  EVAL_TARGETS,
  EVIDENCE_LABELS,
  evalStatusClass,
  fmtDateTime,
  PERSON_DIMENSIONS,
  reviewApi,
  suggestionStatusClass,
  SUGGESTION_STATUSES,
  SUGGESTION_TARGETS,
  suggestionTypeMeta,
  SUGGESTION_TYPES,
  TASK_DIMENSIONS
} from '../api/review.js'
import { PRIORITIES, TASK_TYPES } from '../api/taskOptions.js'
import BaseModal from '../components/BaseModal.vue'

const evaluations = ref([])
const evalPeriod = ref(currentMonth())
const evalTarget = ref('')
const evalLoading = ref(false)
const evalRunning = ref(false)
const evalError = ref('')
const evalRunResult = ref(null)

const evalOpen = ref(false)
const evalDetail = ref(null)
const evalDetailLoading = ref(false)
const evalDetailError = ref('')

const appealOpen = ref(false)
const appealTarget = ref(null)
const appealReason = ref('')
const appealError = ref('')
const appealSubmitting = ref(false)

const suggestions = ref([])
const sugType = ref('')
const sugStatus = ref('PENDING')
const sugTarget = ref('')
const sugLoading = ref(false)
const sugRunning = ref(false)
const sugError = ref('')
const sugRunResult = ref(null)
const acceptingId = ref(null)

const sugOpen = ref(false)
const sugDetail = ref(null)
const sugDetailLoading = ref(false)
const sugDetailError = ref('')

const pageError = ref('')
const notice = ref('')
const noticeTaskId = ref(null)

const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
  } catch {
    return {}
  }
})

const roles = computed(() => user.value.roles || [])
const isManager = computed(() => roles.value.includes('SYS_ADMIN') || roles.value.includes('MANAGER'))
const isOwner = computed(() => roles.value.includes('OWNER'))
const canReview = computed(() => isManager.value || isOwner.value)

function percent(value) {
  return Number(value || 0).toFixed(2)
}

function scoreText(value) {
  return Number(value || 0).toFixed(1)
}

function barWidth(value) {
  const num = Number(value || 0)
  return Math.max(0, Math.min(100, num))
}

function hoursText(value) {
  const num = Number(value || 0)
  return `${num.toFixed(1)} 小时`
}

function priorityLabel(value) {
  return PRIORITIES.find((p) => p.value === value)?.label || '普通'
}

function typeLabel(value) {
  return TASK_TYPES.find((t) => t.value === value)?.label || value || '—'
}

function summaryOf(suggestion) {
  return suggestion?.payload?.summary || '（该建议没有摘要）'
}

function dimensionsOf(evaluation) {
  return evaluation.targetType === 'TASK' ? TASK_DIMENSIONS : PERSON_DIMENSIONS
}

function weightText(scores, code) {
  const weight = scores?.weights?.[code]
  return weight ? `权重 ${weight}` : ''
}

function linkIdsToText(ids) {
  return ids.map((id) => ({ id, text: `#${id}` }))
}

function evidenceRows(evaluation) {
  const ev = evaluation.evidence || {}
  const rows = []
  if (ev.metrics) {
    for (const [key, value] of Object.entries(ev.metrics)) {
      const label = EVIDENCE_LABELS[key] || key
      const suffix = key.endsWith('Rate') ? '%' : ''
      rows.push({ label, value: `${value}${suffix}` })
    }
  }
  if (ev.coAssignCount != null) {
    rows.push({ label: EVIDENCE_LABELS.coAssignCount, value: String(ev.coAssignCount) })
  }
  if (ev.taskId) {
    rows.push({ label: '工单', value: ev.taskNo || '', links: linkIdsToText([ev.taskId]) })
  }
  if (ev.taskIds?.length) {
    rows.push({ label: EVIDENCE_LABELS.taskIds, value: `${ev.taskIds.length} 张`, links: linkIdsToText(ev.taskIds) })
  }
  if (ev.deptName) {
    rows.push({ label: EVIDENCE_LABELS.deptName, value: ev.deptName })
  }
  if (ev.issueIds?.length != null) {
    rows.push({ label: EVIDENCE_LABELS.issueIds, value: `${ev.issueIds.length} 条` })
  }
  if (ev.assigneeIds?.length != null) {
    rows.push({ label: EVIDENCE_LABELS.assigneeIds, value: `${ev.assigneeIds.length} 人` })
  }
  if (ev.delayCount != null) rows.push({ label: EVIDENCE_LABELS.delayCount, value: String(ev.delayCount) })
  if (ev.urgeCount != null) rows.push({ label: EVIDENCE_LABELS.urgeCount, value: String(ev.urgeCount) })
  if (ev.rejectCount != null) rows.push({ label: EVIDENCE_LABELS.rejectCount, value: String(ev.rejectCount) })
  if (ev.planEnd) rows.push({ label: EVIDENCE_LABELS.planEnd, value: ev.planEnd })
  if (ev.overdueDays != null) rows.push({ label: EVIDENCE_LABELS.overdueDays, value: String(ev.overdueDays) })
  if (ev.durationSamples != null) rows.push({ label: EVIDENCE_LABELS.durationSamples, value: String(ev.durationSamples) })
  if (ev.sameTypeDoneCount != null) {
    rows.push({ label: EVIDENCE_LABELS.sameTypeDoneCount, value: String(ev.sameTypeDoneCount) })
  }
  if (ev.avgHours != null) rows.push({ label: EVIDENCE_LABELS.avgHours, value: hoursText(ev.avgHours) })
  if (ev.count != null) rows.push({ label: EVIDENCE_LABELS.count, value: String(ev.count) })
  if (ev.taskType) rows.push({ label: '事项类型', value: ev.taskType })
  return rows
}

function suggestionEvidenceRows(suggestion) {
  const ev = suggestion.evidence || {}
  const rows = []
  if (ev.taskId) {
    rows.push({ label: '工单', value: `${ev.taskNo || ''} #${ev.taskId}` })
  }
  if (ev.deptName) rows.push({ label: '部门', value: ev.deptName })
  if (ev.candidates?.length) rows.push({ label: '候选人', value: ev.candidates.map((id) => `#${id}`).join('、') })
  if (ev.issueIds?.length) rows.push({ label: '未关闭问题', value: `${ev.issueIds.length} 条` })
  if (ev.sameTypeDoneCount != null) rows.push({ label: '同类完成单', value: `${ev.sameTypeDoneCount} 张` })
  if (ev.avgHours != null) rows.push({ label: '历史平均耗时', value: hoursText(ev.avgHours) })
  if (ev.planEnd) rows.push({ label: '计划完成', value: ev.planEnd })
  if (ev.status) rows.push({ label: '工单状态', value: ev.status })
  if (ev.delayCount != null) rows.push({ label: '改期次数', value: String(ev.delayCount) })
  if (ev.urgeCount != null) rows.push({ label: '催办次数', value: String(ev.urgeCount) })
  if (ev.overdueDays != null) rows.push({ label: '逾期天数', value: String(ev.overdueDays) })
  if (ev.count != null) rows.push({ label: '出现次数', value: String(ev.count) })
  if (ev.taskIds?.length) rows.push({ label: '关联工单', value: ev.taskIds.map((id) => `#${id}`).join('、') })
  return rows
}

function appealReasonOf(evaluation) {
  const ev = evaluation.evidence || {}
  return ev.appealReason || ev.reason || '（未记录理由）'
}

function assigneeName(id) {
  const candidate = sugDetail.value?.payload?.candidates?.find((c) => Number(c.employeeId) === Number(id))
  return candidate?.name || `#${id}`
}

function clearNotice() {
  notice.value = ''
  noticeTaskId.value = null
}

async function loadEvaluations() {
  if (!isManager.value) return
  evalLoading.value = true
  evalError.value = ''
  try {
    evaluations.value = await reviewApi.evaluations.list({
      period: evalPeriod.value || undefined,
      targetType: evalTarget.value || undefined,
      limit: 60
    })
  } catch (e) {
    evalError.value = e.message
  } finally {
    evalLoading.value = false
  }
}

async function runEvaluations() {
  evalRunning.value = true
  evalError.value = ''
  evalRunResult.value = null
  try {
    evalRunResult.value = await reviewApi.evaluations.run({
      period: evalPeriod.value || null,
      targetType: evalTarget.value || null
    })
    await loadEvaluations()
  } catch (e) {
    evalError.value = e.message
  } finally {
    evalRunning.value = false
  }
}

async function openEval(evaluation) {
  evalOpen.value = true
  evalDetailLoading.value = true
  evalDetailError.value = ''
  evalDetail.value = evaluation
  try {
    evalDetail.value = await reviewApi.evaluations.detail(evaluation.id)
  } catch (e) {
    evalDetailError.value = e.message
  } finally {
    evalDetailLoading.value = false
  }
}

function openAppeal(evaluation) {
  appealTarget.value = evaluation
  appealReason.value = ''
  appealError.value = ''
  appealOpen.value = true
}

async function submitAppeal() {
  const reason = appealReason.value.trim()
  if (!reason) {
    appealError.value = '请填写申诉理由'
    return
  }
  if (reason.length < 5) {
    appealError.value = '申诉理由至少 5 个字'
    return
  }
  appealSubmitting.value = true
  appealError.value = ''
  try {
    const updated = await reviewApi.evaluations.appeal(appealTarget.value.id, { reason })
    appealOpen.value = false
    evalOpen.value = false
    notice.value = `已提交申诉：${updated.targetName || ''} ${updated.period}`
    noticeTaskId.value = null
    await loadEvaluations()
  } catch (e) {
    appealError.value = e.message
  } finally {
    appealSubmitting.value = false
  }
}

async function loadSuggestions() {
  sugLoading.value = true
  sugError.value = ''
  try {
    suggestions.value = await reviewApi.suggestions.list({
      type: sugType.value || undefined,
      status: sugStatus.value || undefined,
      targetType: sugTarget.value || undefined,
      limit: 60
    })
  } catch (e) {
    sugError.value = e.message
  } finally {
    sugLoading.value = false
  }
}

async function runSuggestions() {
  sugRunning.value = true
  sugError.value = ''
  sugRunResult.value = null
  try {
    sugRunResult.value = await reviewApi.suggestions.run(sugType.value || null)
    sugStatus.value = 'PENDING'
    await loadSuggestions()
    if (sugRunResult.value.created === 0 && sugRunResult.value.notes?.length) {
      notice.value = sugRunResult.value.notes.join('；')
      noticeTaskId.value = null
    }
  } catch (e) {
    sugError.value = e.message
  } finally {
    sugRunning.value = false
  }
}

async function openSuggestion(suggestion) {
  sugOpen.value = true
  sugDetailLoading.value = true
  sugDetailError.value = ''
  sugDetail.value = suggestion
  try {
    sugDetail.value = await reviewApi.suggestions.detail(suggestion.id)
  } catch (e) {
    sugDetailError.value = e.message
  } finally {
    sugDetailLoading.value = false
  }
}

async function acceptSuggestion(suggestion) {
  acceptingId.value = suggestion.id
  sugError.value = ''
  try {
    const result = await reviewApi.suggestions.accept(suggestion.id)
    clearNotice()
    notice.value = result.message
    noticeTaskId.value = result.objectType === 'TASK' ? result.objectId : null
    sugOpen.value = false
    await loadSuggestions()
  } catch (e) {
    sugError.value = e.message
  } finally {
    acceptingId.value = null
  }
}

async function refreshAll() {
  pageError.value = ''
  if (!canReview.value) return
  const tasks = [loadSuggestions()]
  if (isManager.value) tasks.push(loadEvaluations())
  await Promise.all(tasks)
}

onMounted(() => {
  refreshAll().catch((e) => {
    pageError.value = e.message
  })
})
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.notice {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px;
  margin-bottom: 12px;
  border: 1px solid rgba(46, 125, 50, 0.28);
  border-radius: var(--radius-sm);
  background: rgba(46, 125, 50, 0.05);
  color: var(--txt-2);
  font-size: 13px;
}

.block {
  margin-bottom: 12px;
  padding: 14px 16px 16px;
}

.block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.block-head h3 {
  margin: 0 0 2px;
  font-size: 14px;
  font-weight: 600;
}

.block-head p {
  margin: 0;
  line-height: 1.6;
}

.muted {
  color: var(--txt-3);
  font-size: 12px;
}

.small {
  font-size: 12px;
}

.spacer {
  flex: 1;
}

.run-result {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 10px;
  margin-bottom: 10px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  background: #FBFBFC;
}

.target-name {
  font-size: 13px;
  font-weight: 500;
}

.appeal-link {
  margin-left: 10px;
}

.sug-list {
  display: flex;
  flex-direction: column;
}

.sug {
  padding: 12px 4px;
  border-top: 1px solid var(--divider);
}

.sug:first-child {
  border-top: none;
}

.sug.done {
  opacity: 0.82;
}

.sug-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sug-type {
  color: var(--txt);
  font-weight: 500;
}

.sug-target {
  font-size: 12px;
  color: var(--txt-2);
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sug-summary {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--txt);
}

.sug-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.sug-detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.sug-detail-summary {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  background: #FBFBFC;
  font-size: 13px;
  line-height: 1.7;
}

.detail-section {
  margin-bottom: 14px;
}

.detail-title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--txt-2);
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 16px;
  font-size: 12px;
}

.detail-grid > div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 4px 0;
  border-bottom: 1px dashed var(--divider);
}

.table.compact th,
.table.compact td {
  padding: 6px 8px;
  font-size: 12px;
}

.measures {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--txt-2);
}

.accept-result {
  padding: 10px 12px;
  border: 1px solid rgba(46, 125, 50, 0.28);
  border-radius: var(--radius-sm);
  background: rgba(46, 125, 50, 0.05);
}

.accept-raw {
  margin-top: 6px;
  word-break: break-all;
  color: var(--txt-2);
}

.eval-top {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}

.eval-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 84px;
  height: 84px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: #FBFBFC;
}

.eval-score .num {
  font-size: 24px;
  font-weight: 600;
}

.eval-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}

.appeal-reason {
  color: var(--warn);
}

.scores {
  margin-bottom: 14px;
}

.score-row {
  display: grid;
  grid-template-columns: 176px minmax(0, 1fr) 52px 58px;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
}

.score-label {
  display: flex;
  flex-direction: column;
  font-size: 12px;
  font-weight: 500;
}

.bar {
  height: 8px;
  border-radius: 4px;
  background: #EEF0F2;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--txt);
}

.score-value {
  text-align: right;
  font-size: 12px;
}

.weight {
  font-size: 11px;
  color: var(--txt-3);
  text-align: right;
}

.evidence {
  border-top: 1px solid var(--divider);
  padding-top: 10px;
}

.evidence-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--txt-2);
  margin-bottom: 6px;
}

.evidence-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 4px 0;
  font-size: 12px;
  border-bottom: 1px dashed var(--divider);
}

.evidence-label {
  flex: none;
  width: 116px;
  color: var(--txt-3);
}

.evidence-value {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.appeal-tip {
  margin: 0 0 10px;
  line-height: 1.7;
}

.appeal-target {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  font-size: 13px;
}

@media (max-width: 860px) {
  .score-row {
    grid-template-columns: 120px minmax(0, 1fr) 44px;
  }

  .weight {
    display: none;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
