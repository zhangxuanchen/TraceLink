<template>
  <section v-if="detail">
    <div class="back">
      <button class="btn-link" @click="router.push({ name: 'tasks' })">← 返回工单中心</button>
    </div>

    <div class="page-head">
      <div>
        <div class="title-row">
          <span class="no num">{{ task.taskNo }}</span>
          <span class="page-title">{{ task.title }}</span>
          <span class="tag" :class="statusClass(task.status)">{{ task.statusLabel }}</span>
          <span v-if="task.overdue" class="tag tag-bad">
            {{ task.overdueLevelLabel }} · 逾期 {{ task.overdueDays }} 天
          </span>
          <span v-if="task.recurrenceOf" class="tag tag-warn">复发工单</span>
        </div>
        <p class="page-sub">
          {{ task.deptName }} · 负责人 {{ task.ownerName }} · 主责 {{ task.primaryAssigneeName || task.assigneeText || '待认领' }}
        </p>
      </div>
      <div class="ops">
        <button v-if="task.actions.canDispatch" class="btn" @click="openDispatch">派工 / 调整</button>
        <button v-if="task.actions.canCheckin" class="btn btn-primary" @click="openCheckin">写追踪记录</button>
        <button v-if="task.actions.canSubmit" class="btn btn-primary" @click="openSubmit">提交验收</button>
        <button v-if="task.actions.canVerify" class="btn btn-primary" @click="openVerify">验收</button>
        <button v-if="task.actions.canReschedule" class="btn" @click="openReschedule">改期</button>
        <button v-if="task.actions.canUrge" class="btn" @click="urge">催办</button>
        <button v-if="task.actions.canCancel" class="btn btn-danger" @click="openCancel">取消工单</button>
      </div>
    </div>

    <div v-if="error" class="form-error">{{ error }}</div>

    <div class="layout">
      <div class="left">
        <div class="card block">
          <div class="meta">
            <div class="meta-item"><span>类型</span><strong>{{ task.typeLabel }}</strong></div>
            <div class="meta-item"><span>优先级</span><strong>{{ priorityLabel(task.priority) }}</strong></div>
            <div class="meta-item"><span>计划开始</span><strong class="num">{{ formatDateTime(task.planStart) }}</strong></div>
            <div class="meta-item"><span>完成期限</span><strong class="num">{{ formatDateTime(task.planEnd) }}</strong></div>
            <div class="meta-item"><span>实际完成</span><strong class="num">{{ formatDateTime(task.actualEnd) }}</strong></div>
            <div class="meta-item"><span>退回 / 改期 / 催办</span>
              <strong class="num">{{ task.rejectCount }} / {{ task.delayCount }} / {{ task.urgeCount }}</strong>
            </div>
          </div>
          <div class="progress-row">
            <span class="muted">进度</span>
            <div class="bar"><i :style="{ width: task.progress + '%' }"></i></div>
            <span class="num">{{ task.progress }}%</span>
          </div>
          <div v-if="detail.submitGaps.length" class="gaps">
            <div class="gaps-title">还差这些才能提交验收</div>
            <ul>
              <li v-for="gap in detail.submitGaps" :key="gap">{{ gap }}</li>
            </ul>
            <p class="muted">完成条件矩阵在 M3 上线，这里先提示基础缺口。</p>
          </div>
        </div>

        <div class="card block">
          <h3 class="block-title">背景与要求</h3>
          <p class="text">{{ detail.description || '没有填写背景说明' }}</p>
        </div>

        <div class="card block">
          <h3 class="block-title">验收条件</h3>
          <ul v-if="detail.acceptCriteria.length" class="list">
            <li v-for="(item, index) in detail.acceptCriteria" :key="index">{{ item }}</li>
          </ul>
          <p v-else class="muted">未设置验收条件，提交时按结果说明判定。</p>
        </div>

        <div class="card block">
          <h3 class="block-title">问题项 <span class="muted">{{ openIssueCount }} 条未关闭</span></h3>
          <ul v-if="detail.issues.length" class="list">
            <li v-for="issue in detail.issues" :key="issue.id" class="item-row">
              <span class="seq num">{{ issue.seq }}</span>
              <span class="grow">{{ issue.content }}</span>
              <span class="tag" :class="issue.status === 'CLOSED' ? 'tag-ok' : 'tag-bad'">{{ issue.statusLabel }}</span>
              <button v-if="issue.status === 'OPEN' && task.actions.canCheckin" class="btn-link"
                      @click="closeIssue(issue)">关闭</button>
            </li>
          </ul>
          <p v-else class="muted">没有记录问题项。</p>
        </div>

        <div class="card block">
          <h3 class="block-title">措施项 <span class="muted">{{ openActionCount }} 条未完成</span></h3>
          <ul v-if="detail.actions.length" class="list">
            <li v-for="action in detail.actions" :key="action.id" class="item-row">
              <span class="seq num">{{ action.seq }}</span>
              <span class="grow">{{ action.content }}</span>
              <span class="muted num">{{ action.ownerName || '—' }} · {{ action.dueDate || '无期限' }}</span>
              <span v-if="action.overdue" class="tag tag-bad">超期</span>
              <span class="tag" :class="action.status === 'DONE' ? 'tag-ok' : ''">{{ action.statusLabel }}</span>
              <button v-if="action.status === 'OPEN' && task.actions.canCheckin" class="btn-link"
                      @click="doneAction(action)">完成</button>
            </li>
          </ul>
          <p v-else class="muted">没有记录措施项。</p>
        </div>
      </div>

      <div class="right">
        <div class="card block">
          <h3 class="block-title">时间线</h3>
          <div v-if="loadingTimeline" class="empty">加载中…</div>
          <div v-else-if="!timeline.length" class="empty">还没有追踪记录，写第一条说明进展</div>
          <ol v-else class="timeline">
            <li v-for="row in timeline" :key="row.id">
              <div class="dot"></div>
              <div class="tl-body">
                <div class="tl-head">
                  <strong>{{ row.employeeName || '—' }}</strong>
                  <span class="tag" :class="row.resultType === 'RESULT' ? 'tag-ok' : (row.resultType === 'REVIEW' ? 'tag-warn' : '')">
                    {{ row.resultTypeLabel }}
                  </span>
                  <span class="muted num">{{ row.checkDate }}</span>
                  <span class="muted num">{{ row.progress }}%</span>
                  <span v-if="row.backfill" class="tag tag-warn">补录</span>
                </div>
                <p class="text">{{ row.content }}</p>
                <div v-if="row.blockerText" class="blocker">阻碍：{{ row.blockerText }}</div>
                <div v-if="row.nextStep" class="muted">下一步：{{ row.nextStep }}</div>
              </div>
            </li>
          </ol>
        </div>
      </div>
    </div>

    <BaseModal v-if="dialog.open" :title="dialogTitle" @close="dialog.open = false">
      <div v-if="dialog.error" class="form-error">{{ dialog.error }}</div>

      <template v-if="dialog.mode === 'dispatch'">
        <div class="grid-2">
          <div class="field">
            <label>主责执行人</label>
            <select v-model="dialog.form.primaryAssigneeId" :disabled="dialog.saving">
              <option :value="null">保持不变</option>
              <option v-for="e in employees" :key="e.id" :value="e.id">{{ e.name }}（{{ e.deptName }}）</option>
            </select>
          </div>
          <div class="field">
            <label>优先级</label>
            <select v-model.number="dialog.form.priority" :disabled="dialog.saving">
              <option v-for="p in PRIORITIES" :key="p.value" :value="p.value">{{ p.label }}</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>完成期限</label>
          <input v-model="dialog.form.planEnd" type="datetime-local" :disabled="dialog.saving" />
          <span class="muted">只改期限不写原因会累计改期次数，需要留说明请用「改期」。</span>
        </div>
        <div class="field">
          <label>说明</label>
          <textarea v-model="dialog.form.note" rows="2" :disabled="dialog.saving" placeholder="为什么调整，留一句话给执行人"></textarea>
        </div>
      </template>

      <template v-else-if="dialog.mode === 'checkin'">
        <div class="grid-2">
          <div class="field">
            <label>进度<span class="req">*</span></label>
            <input v-model.number="dialog.form.progress" type="number" min="0" max="100" :disabled="dialog.saving" />
          </div>
          <div class="field">
            <label>记录类型<span class="req">*</span></label>
            <select v-model="dialog.form.resultType" :disabled="dialog.saving">
              <option v-for="t in CHECKIN_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>本次做了什么<span class="req">*</span></label>
          <textarea v-model="dialog.form.content" rows="4" :disabled="dialog.saving"
                    placeholder="写具体动作与结果；结果类记录至少 20 字"></textarea>
        </div>
        <div class="field">
          <label>阻碍（填了就把工单置为受阻）</label>
          <textarea v-model="dialog.form.blockerText" rows="2" :disabled="dialog.saving"
                    placeholder="卡在哪里、等谁支持"></textarea>
        </div>
        <div class="field">
          <label>下一步</label>
          <input v-model="dialog.form.nextStep" :disabled="dialog.saving" placeholder="下一件要做什么" />
        </div>
        <div class="grid-2">
          <div class="field">
            <label>业务日期</label>
            <input v-model="dialog.form.checkDate" type="date" :disabled="dialog.saving" />
          </div>
          <div class="field">
            <label>阻碍是否已解除</label>
            <label class="check"><input v-model="dialog.form.resolvedBlocker" type="checkbox" :disabled="dialog.saving" /> 已恢复推进</label>
          </div>
        </div>
      </template>

      <template v-else-if="dialog.mode === 'submit'">
        <div class="field">
          <label>结果说明<span class="req">*</span></label>
          <textarea v-model="dialog.form.resultSummary" rows="4" :disabled="dialog.saving"
                    placeholder="做到了什么程度、用什么证明"></textarea>
        </div>
        <div v-if="detail.acceptCriteria.length" class="field">
          <label>验收条件逐条判定</label>
          <label v-for="(item, index) in detail.acceptCriteria" :key="index" class="check block-check">
            <input v-model="dialog.checks[index]" type="checkbox" :disabled="dialog.saving" /> {{ item }}
          </label>
        </div>
      </template>

      <template v-else-if="dialog.mode === 'verify'">
        <div class="field">
          <label>验收结论<span class="req">*</span></label>
          <div class="radios">
            <label class="check"><input v-model="dialog.form.decision" type="radio" value="PASS" :disabled="dialog.saving" /> 通过</label>
            <label class="check"><input v-model="dialog.form.decision" type="radio" value="REJECT" :disabled="dialog.saving" /> 退回</label>
          </div>
        </div>
        <div class="field">
          <label>{{ dialog.form.decision === 'PASS' ? '结果说明' : '复核说明' }}<span v-if="dialog.form.decision === 'PASS'" class="req">*</span></label>
          <textarea v-model="dialog.form.resultSummary" rows="3" :disabled="dialog.saving"
                    placeholder="通过时写清验收依据；退回时写清复核看到的问题"></textarea>
        </div>
        <div v-if="dialog.form.decision === 'REJECT'" class="field">
          <label>退回理由<span class="req">*</span></label>
          <textarea v-model="dialog.form.rejectReason" rows="3" :disabled="dialog.saving"
                    placeholder="不少于 10 字，写清哪里不达标、要补什么"></textarea>
        </div>
        <div v-if="dialog.form.decision === 'PASS' && detail.acceptCriteria.length" class="field">
          <label>验收条件逐条判定</label>
          <label v-for="(item, index) in detail.acceptCriteria" :key="index" class="check block-check">
            <input v-model="dialog.checks[index]" type="checkbox" :disabled="dialog.saving" /> {{ item }}
          </label>
        </div>
      </template>

      <template v-else-if="dialog.mode === 'reschedule'">
        <div class="grid-2">
          <div class="field">
            <label>新期限<span class="req">*</span></label>
            <input v-model="dialog.form.newPlanEnd" type="datetime-local" :disabled="dialog.saving" />
          </div>
          <div class="field">
            <label>改期原因<span class="req">*</span></label>
            <select v-model="dialog.form.reason" :disabled="dialog.saving">
              <option v-for="r in RESCHEDULE_REASONS" :key="r.value" :value="r.value">{{ r.label }}</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>改期说明<span class="req">*</span></label>
          <textarea v-model="dialog.form.reasonText" rows="3" :disabled="dialog.saving"
                    placeholder="不少于 20 字，写清为什么改；改到更晚需要负责人确认"></textarea>
        </div>
        <div class="field">
          <label>根因（累计改期超过 3 次必填，不少于 20 字）</label>
          <textarea v-model="dialog.form.rootCause" rows="2" :disabled="dialog.saving"
                    placeholder="为什么会一改再改"></textarea>
        </div>
        <div class="field">
          <label>新措施（超过 3 次必填，不少于 10 字）</label>
          <textarea v-model="dialog.form.measure" rows="2" :disabled="dialog.saving"
                    placeholder="这次做什么避免再改"></textarea>
        </div>
      </template>

      <template v-else-if="dialog.mode === 'cancel'">
        <div class="field">
          <label>取消原因<span class="req">*</span></label>
          <textarea v-model="dialog.form.reason" rows="3" :disabled="dialog.saving"
                    placeholder="写清为什么这张单不做了，便于审计回看"></textarea>
        </div>
      </template>

      <template #footer>
        <button class="btn" :disabled="dialog.saving" @click="dialog.open = false">取消</button>
        <button class="btn btn-primary" :disabled="dialog.saving" @click="save">
          {{ dialog.saving ? '提交中…' : '确认' }}
        </button>
      </template>
    </BaseModal>
  </section>
  <section v-else>
    <div class="empty">{{ error || '加载中…' }}</div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseModal from '../components/BaseModal.vue'
import { employeeApi } from '../api/org.js'
import { taskApi } from '../api/task.js'
import {
  CHECKIN_TYPES,
  PRIORITIES,
  RESCHEDULE_REASONS,
  formatDateTime,
  statusClass,
  toApiDateTime,
  toInputDateTime
} from '../api/taskOptions.js'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const timeline = ref([])
const employees = ref([])
const loadingTimeline = ref(true)
const error = ref('')

const dialog = reactive({
  open: false, mode: '', saving: false, error: '', form: {}, checks: []
})

const task = computed(() => detail.value.task)
const openIssueCount = computed(() => detail.value.issues.filter((i) => i.status === 'OPEN').length)
const openActionCount = computed(() => detail.value.actions.filter((a) => a.status === 'OPEN').length)

const dialogTitle = computed(() => ({
  dispatch: '派工与调整',
  checkin: '写追踪记录',
  submit: '提交验收',
  verify: '验收',
  reschedule: '改期',
  cancel: '取消工单'
}[dialog.mode] || ''))

function priorityLabel(value) {
  return PRIORITIES.find((p) => p.value === value)?.label || String(value)
}

async function load() {
  error.value = ''
  try {
    const [detailData, timelineData] = await Promise.all([
      taskApi.detail(route.params.id),
      taskApi.timeline(route.params.id)
    ])
    detail.value = detailData
    timeline.value = timelineData || []
    if (!employees.value.length) {
      employees.value = await employeeApi.options()
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loadingTimeline.value = false
  }
}

function openModal(mode, form, checks = []) {
  dialog.mode = mode
  dialog.form = form
  dialog.checks = checks
  dialog.error = ''
  dialog.saving = false
  dialog.open = true
}

function openDispatch() {
  openModal('dispatch', {
    primaryAssigneeId: null,
    priority: task.value.priority,
    planEnd: toInputDateTime(task.value.planEnd),
    note: ''
  })
}

function openCheckin() {
  openModal('checkin', {
    progress: task.value.progress,
    resultType: 'PROGRESS',
    content: '',
    blockerText: '',
    nextStep: '',
    checkDate: '',
    resolvedBlocker: false
  })
}

function openSubmit() {
  openModal('submit', { resultSummary: '' }, detail.value.acceptCriteria.map(() => false))
}

function openVerify() {
  openModal('verify', { decision: 'PASS', resultSummary: '', rejectReason: '' },
    detail.value.acceptCriteria.map(() => false))
}

function openReschedule() {
  openModal('reschedule', {
    newPlanEnd: toInputDateTime(task.value.planEnd),
    reason: 'CHANGE',
    reasonText: '',
    rootCause: '',
    measure: ''
  })
}

function openCancel() {
  openModal('cancel', { reason: '' })
}

async function save() {
  if (dialog.saving) return
  dialog.error = ''
  const id = task.value.id
  const form = dialog.form
  dialog.saving = true
  try {
    if (dialog.mode === 'dispatch') {
      const nextPlanEnd = toApiDateTime(form.planEnd)
      await taskApi.dispatch(id, {
        primaryAssigneeId: form.primaryAssigneeId,
        planEnd: nextPlanEnd && nextPlanEnd !== task.value.planEnd ? nextPlanEnd : null,
        priority: form.priority,
        note: form.note || null
      })
    } else if (dialog.mode === 'checkin') {
      await taskApi.checkin(id, {
        progress: form.progress,
        content: form.content,
        resultType: form.resultType,
        blockerText: form.blockerText || null,
        nextStep: form.nextStep || null,
        checkDate: form.checkDate || null,
        resolvedBlocker: form.resolvedBlocker
      })
    } else if (dialog.mode === 'submit') {
      await taskApi.submit(id, {
        resultSummary: form.resultSummary,
        criteriaChecks: dialog.checks
      })
    } else if (dialog.mode === 'verify') {
      await taskApi.verify(id, {
        decision: form.decision,
        resultSummary: form.resultSummary || null,
        rejectReason: form.decision === 'REJECT' ? form.rejectReason : null,
        criteriaChecks: form.decision === 'PASS' ? dialog.checks : null
      })
    } else if (dialog.mode === 'reschedule') {
      await taskApi.reschedule(id, {
        newPlanEnd: toApiDateTime(form.newPlanEnd),
        oldPlanEnd: task.value.planEnd,
        reason: form.reason,
        reasonText: form.reasonText,
        rootCause: form.rootCause || null,
        measure: form.measure || null
      })
    } else if (dialog.mode === 'cancel') {
      await taskApi.cancel(id, { reason: form.reason })
    }
    dialog.open = false
    await load()
  } catch (e) {
    dialog.error = e.message
  } finally {
    dialog.saving = false
  }
}

async function urge() {
  if (!window.confirm('向主责执行人发送催办提醒？同一工单 24 小时内只能催办一次。')) return
  error.value = ''
  try {
    await taskApi.urge(task.value.id)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function closeIssue(issue) {
  if (!window.confirm(`关闭问题项「${issue.content}」？`)) return
  error.value = ''
  try {
    const data = await taskApi.closeIssue(task.value.id, issue.id)
    detail.value = data
  } catch (e) {
    error.value = e.message
  }
}

async function doneAction(action) {
  if (!window.confirm(`标记措施「${action.content}」已完成？`)) return
  error.value = ''
  try {
    const data = await taskApi.doneAction(task.value.id, action.id)
    detail.value = data
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<style scoped>
.back {
  margin-bottom: 8px;
}

.title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.no {
  color: var(--txt-3);
  font-size: 13px;
}

.ops {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.left,
.right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.block {
  padding: 16px 18px;
}

.block-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
}

.block-title .muted {
  font-weight: 400;
  margin-left: 8px;
  font-size: 12px;
}

.meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 16px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-item span {
  color: var(--txt-3);
  font-size: 12px;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}

.bar {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: #EDEEF0;
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  background: var(--txt);
}

.gaps {
  margin-top: 14px;
  padding: 10px 12px;
  border: 1px solid rgba(178, 106, 0, 0.24);
  border-radius: var(--radius-sm);
  background: rgba(178, 106, 0, 0.05);
}

.gaps-title {
  color: var(--warn);
  font-size: 13px;
  font-weight: 500;
}

.gaps ul {
  margin: 6px 0 0;
  padding-left: 18px;
  color: var(--txt-2);
  font-size: 13px;
}

.text {
  margin: 0;
  color: var(--txt-2);
  white-space: pre-wrap;
}

.list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.list li {
  padding: 6px 0;
  border-bottom: 1px dashed var(--divider);
  color: var(--txt-2);
}

.list li:last-child {
  border-bottom: none;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.seq {
  color: var(--txt-3);
  width: 18px;
}

.grow {
  flex: 1;
  color: var(--txt);
  min-width: 0;
}

.muted {
  color: var(--txt-3);
  font-size: 12px;
}

.timeline {
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline li {
  display: flex;
  gap: 10px;
  padding-bottom: 16px;
}

.dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--txt);
  flex: none;
}

.tl-body {
  min-width: 0;
  flex: 1;
}

.tl-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
}

.blocker {
  margin-top: 6px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: rgba(198, 40, 40, 0.06);
  color: var(--bad);
  font-size: 13px;
}

.check {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--txt-2);
  font-size: 13px;
}

.block-check {
  padding: 6px 0;
  border-bottom: 1px dashed var(--divider);
}

.radios {
  display: flex;
  gap: 18px;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
