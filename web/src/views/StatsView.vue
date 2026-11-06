<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">统计看板</h2>
        <p class="page-sub">指标口径与工单中心同源：先看三项重点发现，下面按部门和按个人把这七项率摊开</p>
      </div>
    </div>

    <div class="toolbar">
      <div class="presets">
        <button v-for="p in RANGE_PRESETS" :key="p.key" class="chip"
                :class="{ active: preset === p.key }" @click="pickPreset(p)">{{ p.label }}</button>
      </div>
      <input v-model="from" type="date" style="width: 148px" @change="preset = 'custom'" />
      <span class="muted">至</span>
      <input v-model="to" type="date" style="width: 148px" @change="preset = 'custom'" />
      <button class="btn" @click="load">查询</button>
      <ExportPanel compact class="toolbar-export"
                   :codes="['RATES']"
                   :default-from="from" :default-to="to"
                   :dept-id="deptFilter.length === 1 ? deptFilter[0] : null" />
    </div>

    <div v-if="error" class="form-error">{{ error }}</div>

    <template v-if="rates">
      <section class="findings">
        <div class="findings-head">
          <h3>三项重点发现</h3>
          <span class="muted">先看结论，再往下按部门和个人把七项率摊开</span>
        </div>
        <div class="finding-grid">
          <article v-for="(card, index) in findingCards" :key="card.key"
                   class="finding-card" :class="card.level">
            <div class="finding-top">
              <span class="finding-idx">{{ index + 1 }}</span>
              <span class="finding-kind">{{ card.kind }}</span>
            </div>
            <h4 class="finding-title">{{ card.title }}</h4>
            <p class="finding-caliber">{{ card.caliber }}</p>
            <ul v-if="card.rows.length" class="finding-list">
              <li v-for="row in card.rows" :key="row.key"
                  :class="{ clickable: !!card.onRow }" @click="card.onRow && card.onRow(row)">
                <div class="finding-row">
                  <span class="finding-label">{{ row.label }}</span>
                  <span class="finding-meta">{{ row.meta }}</span>
                </div>
                <span class="finding-metric">{{ row.metric }}</span>
              </li>
            </ul>
            <div v-else class="finding-empty">{{ card.empty }}</div>
          </article>
        </div>
      </section>

      <div ref="deptCard" class="card block">
        <div class="chart-head">
          <div class="head-left">
            <h3>部门维度</h3>
            <MultiSelect v-model="deptFilter" :options="deptSelectOptions" placeholder="多选部门条件" />
            <span class="head-hint">按该部门自己的工单重算，样本量见「工单」列</span>
          </div>
        </div>
        <table v-if="deptPageRows.length" class="table">
          <thead>
            <tr>
              <th>部门</th>
              <th class="num">工单</th>
              <th v-for="m in METRICS" :key="m.code" class="num">{{ m.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in deptPageRows" :key="row.id">
              <td>{{ row.name }}</td>
              <td class="num">{{ row.tasks }}</td>
              <td v-for="m in METRICS" :key="m.code" class="num">{{ cell(row, m) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">所选区间没有部门数据</div>
        <div v-if="deptRows.length > PAGE_SIZE" class="pager">
          <span class="muted">共 {{ deptRows.length }} 条 · 第 {{ deptPage }}/{{ deptPages }} 页</span>
          <button class="btn pager-btn" :disabled="deptPage <= 1" @click="deptPage--">上一页</button>
          <button class="btn pager-btn" :disabled="deptPage >= deptPages" @click="deptPage++">下一页</button>
        </div>
      </div>

      <div class="card block">
        <div class="chart-head">
          <div class="head-left">
            <h3>个人维度</h3>
            <input v-model="personKeyword" class="member-search" placeholder="搜索姓名或部门" />
            <span class="head-hint">按主责执行人归集，无主责时归负责人</span>
          </div>
        </div>
        <table v-if="personPageRows.length" class="table">
          <thead>
            <tr>
              <th>姓名</th>
              <th>部门</th>
              <th class="num">工单</th>
              <th v-for="m in METRICS" :key="m.code" class="num">{{ m.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in personPageRows" :key="row.id">
              <td>{{ row.name }}</td>
              <td>{{ row.deptName || '—' }}</td>
              <td class="num">{{ row.tasks }}</td>
              <td v-for="m in METRICS" :key="m.code" class="num">{{ cell(row, m) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">所选条件下没有个人数据</div>
        <div v-if="personRows.length > PAGE_SIZE" class="pager">
          <span class="muted">共 {{ personRows.length }} 条 · 第 {{ personPage }}/{{ personPages }} 页</span>
          <button class="btn pager-btn" :disabled="personPage <= 1" @click="personPage--">上一页</button>
          <button class="btn pager-btn" :disabled="personPage >= personPages" @click="personPage++">下一页</button>
        </div>
      </div>
    </template>

    <div v-else-if="!loading" class="card empty">暂无统计数据，先导入工作日报或建几张工单</div>
    <div v-else class="card empty">加载中…</div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { statsApi, RANGE_PRESETS, rangeOf } from '../api/stats.js'
import { taskApi } from '../api/task.js'
import { gateApi } from '../api/gate.js'
import { deptApi } from '../api/org.js'
import ExportPanel from '../components/ExportPanel.vue'
import MultiSelect from '../components/MultiSelect.vue'

/** 与七项口径卡同码：部门表和个人表都按这个顺序取数 */
const METRICS = [
  { code: 'completion_rate', label: '完成率', unit: '%' },
  { code: 'on_time_rate', label: '按时完成率', unit: '%' },
  { code: 'avg_hours', label: '平均完成时长', unit: 'h' },
  { code: 'overdue_rate', label: '逾期率', unit: '%' },
  { code: 'first_pass_rate', label: '一次通过率', unit: '%' },
  { code: 'evidence_rate', label: '结果留痕率', unit: '%' },
  { code: 'recurrence_rate', label: '复发率', unit: '%' }
]

const PAGE_SIZE = 10

const preset = ref('month')
const from = ref('')
const to = ref('')
const rates = ref(null)
const overdue = ref([])
const gaps = ref(null)
const deptCard = ref(null)
const deptOptions = ref([])
const deptFilter = ref([])
const personKeyword = ref('')
const deptPage = ref(1)
const personPage = ref(1)
const loading = ref(false)
const error = ref('')

const router = useRouter()

const deptSelectOptions = computed(() =>
  deptOptions.value.map((d) => ({ value: d.id, label: '　'.repeat(d.level - 1) + d.name }))
)

/** 选中父部门时把下级部门一起带上，人员按部门名模糊匹配 */
const selectedDeptNames = computed(() => {
  const names = new Set()
  const addWithChildren = (id) => {
    const dept = deptOptions.value.find((d) => d.id === id)
    if (!dept) return
    names.add(dept.name)
    deptOptions.value.filter((d) => d.parentId === id).forEach((d) => addWithChildren(d.id))
  }
  deptFilter.value.forEach(addWithChildren)
  return names
})

const deptRows = computed(() => {
  const rows = rates.value?.depts || []
  if (!deptFilter.value.length) return rows
  return rows.filter((row) => deptFilter.value.includes(row.id))
})

const personRows = computed(() => {
  let rows = rates.value?.persons || []
  if (selectedDeptNames.value.size) {
    rows = rows.filter((row) => row.deptName && selectedDeptNames.value.has(row.deptName))
  }
  const keyword = personKeyword.value.trim().toLowerCase()
  if (keyword) {
    rows = rows.filter((row) => (row.name || '').toLowerCase().includes(keyword)
      || (row.deptName || '').toLowerCase().includes(keyword))
  }
  return rows
})

const deptPages = computed(() => Math.max(1, Math.ceil(deptRows.value.length / PAGE_SIZE)))
const personPages = computed(() => Math.max(1, Math.ceil(personRows.value.length / PAGE_SIZE)))
const deptPageRows = computed(() => pageSlice(deptRows.value, deptPage.value))
const personPageRows = computed(() => pageSlice(personRows.value, personPage.value))

function pageSlice(rows, page) {
  const start = (page - 1) * PAGE_SIZE
  return rows.slice(start, start + PAGE_SIZE)
}

function formatKpi(kpi) {
  const value = Number(kpi.value) || 0
  if (kpi.unit === '%') return value.toFixed(1)
  if (kpi.unit === 'h') return value.toFixed(1)
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

/** 单元格只取这一行自己的同名指标，缺数据留 — */
function cell(row, metric) {
  const kpi = (row.kpis || []).find((item) => item.code === metric.code)
  if (!kpi) return '—'
  return `${formatKpi(kpi)}${metric.unit === '%' ? '%' : ' h'}`
}

function kpiNumber(row, code) {
  const kpi = (row.kpis || []).find((item) => item.code === code)
  return Number(kpi?.value) || 0
}

function focusDeptTable() {
  deptCard.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function applyDept(deptId) {
  deptFilter.value = [deptId]
  deptPage.value = 1
  focusDeptTable()
}

/** 三项结论固定在页首：时效看逾期池，达成看部门完成率，质量看完成条件的积压 */
const findingCards = computed(() => {
  const cards = []

  const overdueDepts = (rates.value?.depts || [])
    .filter((row) => (row.tasks || 0) >= 3 && row.parentId !== 0)
    .map((row) => ({ ...row, overdue: kpiNumber(row, 'overdue_rate') }))
    .sort((a, b) => b.overdue - a.overdue)
  const worstOverdue = overdueDepts[0]
  const overdueMaxDays = (overdue.value || []).reduce((max, task) => Math.max(max, task.overdueDays || 0), 0)
  cards.push({
    key: 'timeliness',
    kind: '时效',
    level: !worstOverdue ? '' : overdueMaxDays >= 7 ? 'lv-bad' : overdueMaxDays >= 3 ? 'lv-warn' : '',
    title: worstOverdue ? `${worstOverdue.name} 逾期率 ${worstOverdue.overdue.toFixed(1)}%` : '当前区间没有足够样本',
    caliber: '口径：部门逾期率 = 期末逾期单 ÷ 近90天全部工单（含已完成），取逾期率最高的前 3 个业务部门；工单数 ≥3、排除顶级根部门才参与比较。另有最久逾期 ≥7 天标红、3–6 天标黄。',
    rows: overdueDepts.slice(0, 3).map((row) => ({
      key: `dept-${row.id}`,
      label: row.name,
      meta: `${row.tasks} 张工单样本`,
      metric: `${row.overdue.toFixed(1)}%`,
      deptId: row.id
    })),
    empty: '区间内没有样本量 ≥3 的部门',
    onRow: (row) => router.push({
      path: '/tasks',
      query: { overdue: '1', deptId: String(row.deptId) }
    })
  })

  const depts = (rates.value?.depts || [])
    .filter((row) => (row.tasks || 0) >= 3)
    .map((row) => ({ ...row, completion: kpiNumber(row, 'completion_rate') }))
    .sort((a, b) => a.completion - b.completion)
  const worstDept = depts[0]
  cards.push({
    key: 'attainment',
    kind: '达成',
    level: !worstDept ? '' : worstDept.completion < 50 ? 'lv-bad' : worstDept.completion < 80 ? 'lv-warn' : '',
    title: worstDept ? `${worstDept.name} 完成率 ${worstDept.completion.toFixed(1)}%` : '当前区间没有足够样本',
    caliber: '口径：当前区间应完成的单中，验收通过数 ÷ 应完成数，按部门重算；工单数 ≥3 的部门才参与比较，<50% 标红、<80% 标黄。',
    rows: depts.slice(0, 3).map((row) => ({
      key: `dept-${row.id}`,
      label: row.name,
      meta: `${row.tasks} 张工单样本`,
      metric: `${row.completion.toFixed(1)}%`,
      deptId: row.id
    })),
    empty: '区间内没有样本量 ≥3 的部门',
    onRow: (row) => applyDept(row.deptId)
  })

  const gapDepts = [...(gaps.value?.byDept || [])]
    .filter((row) => (row.totalCount || 0) >= 9)
    .map((row) => ({
      ...row,
      gapRate: row.totalCount ? (row.todoCount / row.totalCount) * 100 : 0
    }))
    .sort((a, b) => b.gapRate - a.gapRate || (b.todoCount || 0) - (a.todoCount || 0))
  const worstGapDept = gapDepts[0]
  const gapMaxDays = gapDepts.reduce((max, dept) => Math.max(max, dept.maxGapDays || 0), 0)
  cards.push({
    key: 'quality',
    kind: '质量',
    level: !worstGapDept ? '' : gapMaxDays >= 7 ? 'lv-bad' : gapMaxDays >= 3 ? 'lv-warn' : '',
    title: worstGapDept ? `${worstGapDept.deptName} 缺口率 ${worstGapDept.gapRate.toFixed(1)}%` : '当前区间没有足够样本',
    caliber: '口径：部门缺口率 = 未完成的完成条件项 ÷ 应达成项（不含不适用），取缺口率最高的前 3 个部门；应达成项 ≥9 的部门才参与比较；另有最久滞留 ≥7 天标红、3–6 天标黄。',
    rows: gapDepts.slice(0, 3).map((dept) => ({
      key: `dept-${dept.deptId}`,
      label: dept.deptName,
      meta: `待完成 ${dept.todoCount} / 应达成 ${dept.totalCount}`,
      metric: `${dept.gapRate.toFixed(1)}%`,
      deptId: dept.deptId
    })),
    empty: '当前没有应达成项 ≥9 的部门',
    onRow: (row) => router.push({
      path: '/insights',
      query: row.deptId ? { tab: 'map', deptId: String(row.deptId) } : { tab: 'map' }
    })
  })

  return cards
})

function pickPreset(item) {
  preset.value = item.key
  if (item.days === null) return
  const range = rangeOf(item)
  from.value = range.from
  to.value = range.to
  load()
}

async function load() {
  if (!from.value || !to.value) return
  loading.value = true
  error.value = ''
  const [ratesRes, overdueRes, gapsRes] = await Promise.allSettled([
    statsApi.rates({ from: from.value, to: to.value }),
    taskApi.overdue(),
    gateApi.gaps()
  ])
  if (ratesRes.status === 'fulfilled') {
    rates.value = ratesRes.value
  } else {
    error.value = ratesRes.reason?.message || '统计数据加载失败'
    rates.value = null
  }
  overdue.value = overdueRes.status === 'fulfilled' ? (overdueRes.value || []) : []
  gaps.value = gapsRes.status === 'fulfilled' ? gapsRes.value : null
  loading.value = false
}

async function loadRefs() {
  try {
    const tree = await deptApi.tree()
    deptOptions.value = flatten(tree)
  } catch (e) {
    deptOptions.value = []
  }
}

function flatten(nodes, level = 1, parentId = null) {
  const list = []
  for (const node of nodes || []) {
    list.push({ id: node.id, name: node.name, level, parentId })
    list.push(...flatten(node.children, level + 1, node.id))
  }
  return list
}

/** 条件变化回到第一页，数据变少时把页码收进有效范围 */
watch([deptFilter, personKeyword], () => {
  deptPage.value = 1
  personPage.value = 1
})
watch(deptPages, (pages) => {
  if (deptPage.value > pages) deptPage.value = pages
})
watch(personPages, (pages) => {
  if (personPage.value > pages) personPage.value = pages
})

onMounted(async () => {
  const range = rangeOf(RANGE_PRESETS[1])
  from.value = range.from
  to.value = range.to
  await loadRefs()
  await load()
})
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
}

.presets {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.chip {
  height: 32px;
  padding: 0 12px;
  border: none;
  background: var(--surface);
  color: var(--txt-2);
  cursor: pointer;
  font-size: 13px;
}

.chip.active {
  background: var(--txt);
  color: #fff;
}

.muted {
  color: var(--txt-3);
  font-size: 12px;
}

.chart-head {
  display: flex;
  align-items: center;
  padding: 16px 12px 12px;
}

.head-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 16px;
  min-width: 0;
}

.head-hint {
  color: var(--txt-3);
  font-size: 12px;
}

.chart-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 32px;
  white-space: nowrap;
}

.member-search {
  width: 180px;
  height: 32px;
}

.table .num {
  text-align: right;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 12px 12px;
}

/* 导出控件放在查询按钮右边，空间不足时整组换行靠右 */
.toolbar-export {
  margin-left: auto;
  max-width: 100%;
}

.pager-btn {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
}

.block {
  margin-bottom: 12px;
}

.findings {
  margin-bottom: 12px;
}

.findings-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.findings-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.finding-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.finding-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-left: 3px solid var(--divider);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
}

.finding-card.lv-warn {
  border-left-color: var(--warn);
}

.finding-card.lv-bad {
  border-left-color: var(--bad);
}

.finding-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.finding-idx {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--txt);
  color: #fff;
  font-size: 11px;
}

.finding-kind {
  font-size: 12px;
  color: var(--txt-3);
}

.finding-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.finding-caliber {
  margin: 0;
  color: var(--txt-3);
  font-size: 12px;
  line-height: 1.5;
}

.finding-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.finding-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  background: var(--canvas);
}

.finding-list li.clickable {
  cursor: pointer;
}

.finding-list li.clickable:hover {
  background: var(--divider);
}

.finding-row {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.finding-label {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.finding-meta {
  color: var(--txt-3);
  font-size: 12px;
}

.finding-metric {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.finding-empty {
  padding: 10px 0;
  color: var(--txt-3);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .finding-grid {
    grid-template-columns: 1fr;
  }
}
</style>
