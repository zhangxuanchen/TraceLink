<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">完成地图</h2>
        <p class="page-sub">九条条件逐条判定：矩阵看部门积压，排行看滞留，事项视图看一件事反复卡在哪一步</p>
      </div>
      <div class="head-actions">
        <span v-if="inboxTotal" class="unread" @click="tab = 'inbox'">未读提醒 {{ inboxTotal }}</span>
        <button class="btn" :disabled="loading" @click="refresh">{{ loading ? '加载中…' : '刷新' }}</button>
        <button v-if="canManage" class="btn btn-primary" :disabled="snapshotting" @click="runSnapshot">
          {{ snapshotting ? '重算中…' : '重算快照' }}
        </button>
      </div>
    </div>

    <div class="toolbar">
      <div class="tabs">
        <button v-for="item in visibleTabs" :key="item.key" class="tab"
                :class="{ active: tab === item.key }" @click="switchTab(item.key)">{{ item.label }}</button>
      </div>
      <select v-if="canManage && (tab === 'matrix' || tab === 'gaps')" v-model="deptId" @change="loadMapData">
        <option :value="null">全部部门</option>
        <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ '　'.repeat(d.level - 1) + d.name }}</option>
      </select>
      <select v-if="canManage && tab === 'matrix'" v-model="taskType" @change="loadMatrix">
        <option v-for="t in TASK_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
      </select>
      <span v-if="snapshotHint" class="muted">{{ snapshotHint }}</span>
    </div>

    <div v-if="snapshotStale" class="banner">
      数据截止 {{ snapshotAt || '尚未生成' }}，今日快照还没跑出来：矩阵与排行读的是上一次截面。
      <button v-if="canManage" class="btn-link" @click="runSnapshot">立即重算</button>
    </div>
    <div v-if="error" class="form-error">{{ error }}</div>

    <!-- 部门缺口矩阵 -->
    <div v-if="tab === 'matrix'" class="card map-card">
      <table v-if="matrix" class="table matrix">
        <thead>
          <tr>
            <th class="col-task">工单</th>
            <th class="col-holder">主责</th>
            <th class="col-rate num">完成度</th>
            <th v-for="c in matrix.columns" :key="c.code" class="gate-col"
                :class="{ sorted: sortCode === c.code }" @click="sortBy(c.code)">
              <span class="gate-code num">{{ c.code }}</span>
              <span class="gate-name">{{ c.name }}</span>
              <span class="gate-count num" :class="{ hot: c.todoCount > 0 }">{{ c.todoCount }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in sortedRows" :key="row.taskId" :class="{ 'row-overdue': row.overdue }"
              @click="openTask(row.taskId)">
            <td class="col-task">
              <div class="t-no num">{{ row.taskNo }}</div>
              <div class="t-title">{{ row.title }}</div>
            </td>
            <td class="col-holder">{{ row.assigneeName || '待认领' }}</td>
            <td class="col-rate num">{{ row.completionRate }}%</td>
            <td v-for="cell in row.cells" :key="cell.code" class="gate-cell" :class="cellClass(cell)">
              <template v-if="cell.state === 'DONE'">达成</template>
              <template v-else-if="cell.state === 'TODO'"><b class="num">{{ cell.gapDays }}</b> 天</template>
              <template v-else>—</template>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="!matrix" class="empty">加载中…</div>
      <div v-else-if="!matrix.rows.length" class="empty">该部门没有在办工单</div>
      <div v-if="matrix && matrix.total > matrix.rows.length" class="table-foot">
        共 {{ matrix.total }} 张在办工单，当前展示前 {{ matrix.rows.length }} 张，按逾期与工单号排序
      </div>
    </div>

    <!-- 缺口排行 -->
    <div v-if="tab === 'gaps'" class="gap-grid">
      <div class="card block">
        <h3 class="block-title">按条件积压 <span class="muted">共 {{ gaps ? gaps.totalTodo : 0 }} 个缺口</span></h3>
        <div v-if="gaps && gaps.byGate.length" class="bar-list">
          <div v-for="g in gaps.byGate" :key="g.code" class="bar-row">
            <span class="bar-label"><b class="num">{{ g.code }}</b> {{ g.name }}</span>
            <div class="bar"><i :style="{ width: barWidth(g.todoCount) }"></i></div>
            <span class="num bar-value">{{ g.todoCount }}</span>
            <span class="muted bar-extra">最久 {{ g.maxGapDays }} 天</span>
          </div>
        </div>
        <div v-else class="empty">没有缺口</div>
      </div>

      <div class="card block">
        <h3 class="block-title">按承担者积压</h3>
        <ul v-if="gaps && gaps.byHolder.length" class="rank-list">
          <li v-for="h in gaps.byHolder" :key="h.holderId || 'none'">
            <span>{{ h.holderName || '未指定' }}</span>
            <span class="num">{{ h.todoCount }} 个 · 最久 {{ h.maxGapDays }} 天</span>
          </li>
        </ul>
        <div v-else class="empty">没有缺口</div>
      </div>

      <div class="card block wide">
        <h3 class="block-title">滞留最久的前 20 条</h3>
        <table v-if="gaps && gaps.top.length" class="table">
          <thead>
            <tr><th>工单</th><th>条件</th><th>承担者</th><th class="num">滞留</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in gaps.top" :key="item.taskId + item.gateCode + item.gapDays"
                @click="openTask(item.taskId)">
              <td>
                <div class="t-no num">{{ item.taskNo }}</div>
                <div class="t-title">{{ item.title }}</div>
              </td>
              <td>{{ item.gateCode }} {{ item.gateName }}</td>
              <td>{{ item.holderName || '未指定' }}</td>
              <td class="num"><span class="tag tag-bad">{{ item.gapDays }} 天</span></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">没有超期缺口</div>
      </div>
    </div>

    <!-- 事项视图 -->
    <div v-if="tab === 'matter'" class="card block">
      <h3 class="block-title">事项完成地图</h3>
      <p class="muted">输入任意一张工单 ID，会把父子单与复发链合成一件事，并按时间轴给出每张单停在哪一步。</p>
      <div class="matter-search">
        <input v-model="matterId" placeholder="工单 ID，如 1" @keyup.enter="loadMatter" />
        <button class="btn" :disabled="matterLoading" @click="loadMatter">{{ matterLoading ? '读取中…' : '查看事项' }}</button>
      </div>
      <div v-if="matterError" class="form-error">{{ matterError }}</div>
      <template v-if="matter">
        <div class="matter-head">
          <strong>{{ matter.matterTitle }}</strong>
          <span class="muted">共 {{ matter.tasks.length }} 张单</span>
          <span v-if="matter.stuckGateCode" class="tag tag-bad">
            反复停在 {{ matter.stuckGateCode }} {{ matter.stuckGateName }} · {{ matter.stuckCount }} 次
          </span>
        </div>
        <ol class="matter-list">
          <li v-for="t in matter.tasks" :key="t.taskId">
            <div class="matter-row">
              <button class="btn-link num" @click="openTask(t.taskId)">{{ t.taskNo }}</button>
              <span class="grow">{{ t.title }}</span>
              <span class="tag" :class="t.status === 'DONE' ? 'tag-ok' : (t.status === 'BLOCKED' ? 'tag-bad' : '')">
                {{ t.statusLabel }}
              </span>
              <span class="muted num">{{ t.createdAt }}</span>
              <span class="num">{{ t.completionRate }}%</span>
            </div>
            <div class="gate-chips">
              <span v-for="g in t.gates" :key="g.code" class="gate-chip"
                    :class="[chipClass(g), { stuck: g.code === matter.stuckGateCode }]">
                {{ g.code }} {{ g.stateLabel }}<b v-if="g.state === 'TODO' && g.gapDays > 1" class="num"> · {{ g.gapDays }} 天</b>
              </span>
            </div>
          </li>
        </ol>
      </template>
    </div>

    <!-- 我的提醒 -->
    <div v-if="tab === 'inbox'" class="card block">
      <h3 class="block-title">
        我的缺口提醒 <span class="muted">未读 {{ inboxTotal }} 条</span>
        <button class="btn-link head-link" @click="loadNotifications">刷新</button>
      </h3>
      <ul v-if="notifications.length" class="inbox">
        <li v-for="n in notifications" :key="n.id" :class="{ unread: !n.read }">
          <div class="inbox-head">
            <span class="tag" :class="levelTag(n.level)">{{ n.levelLabel }}</span>
            <span class="num">{{ n.taskNo }}</span>
            <span>{{ n.gateName }}</span>
            <span class="muted">{{ n.createdAt }}</span>
          </div>
          <p class="text">{{ n.content }}</p>
          <div class="inbox-ops">
            <button class="btn-link" @click="openTask(n.taskId)">查看工单</button>
            <button v-if="!n.read" class="btn-link" @click="readOne(n)">标记已读</button>
          </div>
        </li>
      </ul>
      <div v-else class="empty">还没有提醒。缺口首次出现、滞留三日与七日时，这里会收到消息。</div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { gateApi, TASK_TYPES } from '../api/gate.js'
import { deptApi } from '../api/org.js'
import { USER_KEY } from '../api/http.js'

const route = useRoute()
const router = useRouter()

const TABS = [
  { key: 'matrix', label: '部门矩阵', manage: true },
  { key: 'gaps', label: '缺口排行', manage: true },
  { key: 'matter', label: '事项视图', manage: false },
  { key: 'inbox', label: '我的提醒', manage: false }
]

const tab = ref('matrix')
const deptId = ref(null)
const taskType = ref(null)
const deptOptions = ref([])
const matrix = ref(null)
const gaps = ref(null)
const matter = ref(null)
const matterId = ref('')
const notifications = ref([])
const inboxTotal = ref(0)
const sortCode = ref('')
const loading = ref(false)
const snapshotting = ref(false)
const matterLoading = ref(false)
const error = ref('')
const matterError = ref('')

const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
  } catch {
    return {}
  }
})
const canManage = computed(() => {
  const roles = user.value.roles || []
  return roles.includes('SYS_ADMIN') || roles.includes('MANAGER') || roles.includes('OWNER')
})
const visibleTabs = computed(() => TABS.filter((t) => !t.manage || canManage.value))

const snapshotAt = computed(() => matrix.value?.snapshotAt || gaps.value?.snapshotAt || '')
const snapshotStale = computed(() => Boolean(matrix.value?.stale || gaps.value?.stale))
const snapshotHint = computed(() => {
  if (tab.value !== 'matrix' && tab.value !== 'gaps') return ''
  if (!matrix.value) return ''
  return `快照 ${snapshotAt.value || '—'} · 在办 ${matrix.value.total} 张`
})

const sortedRows = computed(() => {
  const rows = [...(matrix.value?.rows || [])]
  if (!sortCode.value) return rows
  const index = matrix.value.columns.findIndex((c) => c.code === sortCode.value)
  if (index < 0) return rows
  const weight = (cell) => (cell.state === 'TODO' ? 0 : (cell.state === 'NA' ? 2 : 1))
  return rows.sort((a, b) => {
    const ca = a.cells[index]
    const cb = b.cells[index]
    if (weight(ca) !== weight(cb)) return weight(ca) - weight(cb)
    return cb.gapDays - ca.gapDays
  })
})

function cellClass(cell) {
  if (cell.state === 'DONE') return 'cell-done'
  if (cell.state === 'TODO') return 'cell-todo'
  return 'cell-na'
}

function chipClass(gate) {
  if (gate.state === 'DONE') return 'chip-done'
  if (gate.state === 'TODO') return 'chip-todo'
  return 'chip-na'
}

function levelTag(level) {
  if (level === 'SECOND') return 'tag-bad'
  if (level === 'FIRST') return 'tag-warn'
  return ''
}

function barWidth(count) {
  const max = Math.max(1, gaps.value?.totalTodo || 1)
  const top = Math.max(1, ...(gaps.value?.byGate || []).map((g) => g.todoCount))
  return `${Math.round((count / Math.max(max, top)) * 100)}%`
}

function flatten(nodes, level = 1) {
  const list = []
  for (const node of nodes || []) {
    list.push({ id: node.id, name: node.name, level })
    list.push(...flatten(node.children, level + 1))
  }
  return list
}

function openTask(id) {
  if (id) router.push({ name: 'task-detail', params: { id } })
}

async function loadRefs() {
  try {
    const tree = await deptApi.tree()
    deptOptions.value = flatten(tree)
  } catch (e) {
    deptOptions.value = []
  }
}

async function loadMatrix() {
  error.value = ''
  try {
    matrix.value = await gateApi.matrix({
      deptId: deptId.value || undefined,
      taskType: taskType.value || undefined,
      page: 1,
      pageSize: 200
    })
  } catch (e) {
    matrix.value = null
    error.value = e.message
  }
}

async function loadGaps() {
  error.value = ''
  try {
    gaps.value = await gateApi.gaps({ deptId: deptId.value || undefined })
  } catch (e) {
    gaps.value = null
    error.value = e.message
  }
}

async function loadNotifications() {
  try {
    const data = await gateApi.notifications({ unreadOnly: false, limit: 30 })
    notifications.value = data.records || []
    inboxTotal.value = data.total || 0
  } catch (e) {
    notifications.value = []
    inboxTotal.value = 0
  }
}

async function loadMapData() {
  loading.value = true
  if (tab.value === 'matrix') {
    await loadMatrix()
  } else if (tab.value === 'gaps') {
    await loadGaps()
  }
  loading.value = false
}

async function loadMatter() {
  const id = String(matterId.value || '').trim()
  if (!id) {
    matterError.value = '请先填工单 ID'
    return
  }
  matterLoading.value = true
  matterError.value = ''
  try {
    matter.value = await gateApi.matter(id)
  } catch (e) {
    matter.value = null
    matterError.value = e.message
  } finally {
    matterLoading.value = false
  }
}

async function runSnapshot() {
  snapshotting.value = true
  error.value = ''
  try {
    const result = await gateApi.snapshot()
    await loadMatrix()
    if (tab.value === 'gaps') await loadGaps()
    window.alert(`快照已重算：${result.taskCount} 张在办单、${result.cellCount} 个格子、${result.notificationCount} 条新提醒，用时 ${result.durationMs} ms`)
  } catch (e) {
    error.value = e.message
  } finally {
    snapshotting.value = false
  }
}

async function readOne(item) {
  try {
    await gateApi.readNotification(item.id)
    item.read = true
    inboxTotal.value = Math.max(0, inboxTotal.value - 1)
  } catch (e) {
    error.value = e.message
  }
}

function switchTab(key) {
  tab.value = key
  error.value = ''
  if (key === 'matrix' && !matrix.value) loadMatrix()
  if (key === 'gaps' && !gaps.value) loadGaps()
  if (key === 'inbox') loadNotifications()
}

function sortBy(code) {
  sortCode.value = sortCode.value === code ? '' : code
}

async function refresh() {
  if (tab.value === 'matrix') await loadMatrix()
  if (tab.value === 'gaps') await loadGaps()
  if (tab.value === 'matter' && matterId.value) await loadMatter()
  if (tab.value === 'inbox') await loadNotifications()
  await loadNotifications()
}

onMounted(async () => {
  await loadRefs()
  await loadNotifications()
  if (!canManage.value) {
    tab.value = 'inbox'
    return
  }
  if (route.query.taskId) {
    tab.value = 'matter'
    matterId.value = String(route.query.taskId)
    await loadMatter()
    return
  }
  await loadMatrix()
})
</script>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.unread {
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 11px;
  background: rgba(198, 40, 40, 0.08);
  color: var(--bad);
  font-size: 12px;
}

.tabs {
  display: inline-flex;
  padding: 3px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

.tab {
  height: 28px;
  padding: 0 12px;
  border: none;
  border-radius: 6px;
  background: none;
  color: var(--txt-2);
  cursor: pointer;
}

.tab.active {
  background: var(--txt);
  color: #fff;
}

.banner {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid rgba(178, 106, 0, 0.28);
  border-radius: var(--radius-sm);
  background: rgba(178, 106, 0, 0.08);
  color: var(--warn);
  font-size: 13px;
}

.map-card {
  padding: 0;
}

.matrix th,
.matrix td {
  white-space: nowrap;
}

.matrix tbody tr {
  cursor: pointer;
}

.col-task {
  min-width: 240px;
  max-width: 360px;
  white-space: normal;
}

.t-no {
  color: var(--txt-3);
  font-size: 12px;
}

.t-title {
  font-weight: 500;
}

.col-rate {
  text-align: right;
}

.gate-col {
  cursor: pointer;
  text-align: center;
  user-select: none;
}

.gate-col.sorted {
  background: rgba(10, 108, 255, 0.08);
}

.gate-code {
  display: block;
  font-size: 12px;
  color: var(--txt-2);
}

.gate-name {
  display: block;
  font-size: 12px;
}

.gate-count {
  display: inline-block;
  margin-top: 2px;
  padding: 0 6px;
  border-radius: 9px;
  font-size: 11px;
  color: var(--txt-3);
  border: 1px solid var(--divider);
}

.gate-count.hot {
  color: var(--bad);
  border-color: rgba(198, 40, 40, 0.28);
  background: rgba(198, 40, 40, 0.08);
}

.gate-cell {
  text-align: center;
  font-size: 12px;
}

.cell-done {
  color: var(--ok);
  background: rgba(46, 125, 50, 0.05);
}

.cell-todo {
  color: var(--bad);
  background: rgba(198, 40, 40, 0.06);
  font-weight: 500;
}

.cell-na {
  color: var(--txt-3);
}

.row-overdue .t-title {
  color: var(--bad);
}

.table-foot {
  padding: 10px 12px;
  color: var(--txt-3);
  font-size: 12px;
}

.gap-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 12px;
}

.wide {
  grid-column: 1 / -1;
}

.block {
  padding: 14px 16px;
}

.block-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.head-link {
  margin-left: auto;
}

.bar-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bar-row {
  display: grid;
  grid-template-columns: 150px 1fr 48px 82px;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.bar {
  height: 8px;
  border-radius: 4px;
  background: rgba(198, 40, 40, 0.1);
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  background: var(--bad);
}

.bar-value {
  text-align: right;
}

.bar-extra {
  font-size: 12px;
  text-align: right;
}

.rank-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
}

.rank-list li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid var(--divider);
  font-size: 13px;
}

.rank-list li:last-child {
  border-bottom: none;
}

.matter-search {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.matter-search input {
  width: 180px;
}

.matter-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.matter-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.matter-list > li {
  padding: 10px 0;
  border-top: 1px solid var(--divider);
}

.matter-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.grow {
  flex: 1;
  min-width: 0;
}

.gate-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.gate-chip {
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  border: 1px solid var(--divider);
}

.chip-done {
  color: var(--ok);
  border-color: rgba(46, 125, 50, 0.28);
}

.chip-todo {
  color: var(--bad);
  border-color: rgba(198, 40, 40, 0.28);
}

.chip-na {
  color: var(--txt-3);
}

.gate-chip.stuck {
  box-shadow: 0 0 0 2px rgba(198, 40, 40, 0.2);
  font-weight: 600;
}

.inbox {
  margin: 0;
  padding: 0;
  list-style: none;
}

.inbox li {
  padding: 10px 0;
  border-top: 1px solid var(--divider);
}

.inbox li.unread {
  background: rgba(10, 108, 255, 0.04);
}

.inbox-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
}

.text {
  margin: 6px 0;
  color: var(--txt-2);
  font-size: 13px;
}

.inbox-ops {
  display: flex;
  gap: 12px;
}

@media (max-width: 1200px) {
  .gap-grid {
    grid-template-columns: 1fr;
  }
}
</style>
