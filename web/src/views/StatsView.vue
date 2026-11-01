<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">统计看板</h2>
        <p class="page-sub">指标口径与导出报告同源；驾驶舱看节奏，部门看板找主要矛盾，人员分析看画像</p>
      </div>
      <div class="head-actions">
        <button class="btn" :disabled="loading" @click="load">刷新</button>
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
      <select v-model="deptId" style="min-width: 140px">
        <option :value="null">全部部门</option>
        <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ '　'.repeat(d.level - 1) + d.name }}</option>
      </select>
      <button class="btn" @click="load">查询</button>
    </div>

    <div v-if="error" class="form-error">{{ error }}</div>

    <template v-if="overview">
      <div class="kpis">
        <div v-for="kpi in overview.kpis" :key="kpi.code" class="kpi" :class="kpiTone(kpi)">
          <div class="kpi-label">{{ kpi.label }}</div>
          <div class="kpi-value num">{{ formatKpi(kpi) }}<span class="unit">{{ kpi.unit }}</span></div>
          <div class="kpi-formula">{{ kpi.formula }}</div>
        </div>
      </div>

      <div class="chart-grid">
        <div class="card chart-card wide">
          <div class="chart-head">
            <h3>新增与完成趋势</h3>
            <span class="muted">区间 {{ overview.from }} ~ {{ overview.to }}</span>
          </div>
          <LineChart :labels="trendLabels" :series="trendSeries" />
        </div>

        <div class="card chart-card">
          <div class="chart-head"><h3>状态分布</h3></div>
          <DonutChart :items="statusItems" />
        </div>

        <div class="card chart-card">
          <div class="chart-head"><h3>部门完成率</h3><span class="muted">基准线 90%</span></div>
          <BarChart horizontal :items="deptBars" :target="90" :limit="10" />
        </div>

        <div class="card chart-card">
          <div class="chart-head"><h3>逾期时长分布</h3></div>
          <BarChart vertical :items="overdueBinItems" />
        </div>

        <div class="card chart-card">
          <div class="chart-head"><h3>逾期工单前十</h3><span class="muted">点击直达工单</span></div>
          <div v-if="overview.overdueTop.length" class="top-list">
            <button v-for="item in overview.overdueTop" :key="item.id" class="top-item"
                    @click="openTask(item)">
              <span class="top-title">{{ item.title }}</span>
              <span class="top-meta">
                <span>{{ item.deptName || '—' }}</span>
                <span>{{ item.assigneeName || '待认领' }}</span>
                <span class="tag tag-bad">{{ item.overdueDays }} 天</span>
              </span>
            </button>
          </div>
          <div v-else class="empty">区间内没有逾期工单</div>
        </div>

        <div class="card chart-card">
          <div class="chart-head"><h3>复发事项榜</h3></div>
          <BarChart horizontal :items="recurrentItems" :limit="8" :palette="['var(--bad)']" />
        </div>

        <div class="card chart-card">
          <div class="chart-head"><h3>问题类型前十</h3></div>
          <BarChart horizontal :items="typeItems" :limit="10" :palette="['var(--accent)']" />
        </div>

        <div class="card chart-card wide">
          <div class="chart-head"><h3>部门周热力</h3><span class="muted">颜色越深，逾期越密</span></div>
          <HeatmapChart :cells="heatCells" />
        </div>
      </div>

      <div class="card chart-card block">
        <div class="chart-head">
          <h3>计划与完成对比</h3>
          <span class="muted">来自日报表尾生产块，口径 = 实际产量 ÷ 计划产量</span>
        </div>
        <PairBarChart v-if="process" :items="process.byProcess" :limit="10" />
        <div v-else class="empty">所选区间没有生产指标</div>
      </div>

      <div class="person-block">
        <div class="card chart-card">
          <div class="chart-head">
            <h3>人员产出与逾期</h3>
            <select v-model="employeeId" style="width: 150px" @change="load">
              <option :value="null">全部人员</option>
              <option v-for="e in employeeOptions" :key="e.id" :value="e.id">{{ e.name }}</option>
            </select>
          </div>
          <ScatterChart v-if="person" :points="scatterPoints" x-label="验收单数" y-label="在办逾期" />
        </div>

        <div class="card chart-card">
          <div class="chart-head">
            <h3>能力雷达 · {{ person?.focusName || '—' }}</h3>
            <span class="muted">六维分值 0-100</span>
          </div>
          <RadarChart v-if="person" :dims="person.radar" />
        </div>

        <div class="card chart-card">
          <div class="chart-head"><h3>完成漏斗</h3><span class="muted">卡在哪一步流失</span></div>
          <FunnelChart v-if="person" :steps="funnelSteps" />
        </div>
      </div>

      <div class="card block">
        <div class="chart-head"><h3>人员绩效明细</h3></div>
        <table v-if="person?.persons?.length" class="table">
          <thead>
            <tr>
              <th>姓名</th><th>部门</th><th class="num">应完成</th><th class="num">验收</th><th class="num">在办</th>
              <th class="num">逾期</th><th class="num">退回</th><th class="num">按时率</th>
              <th class="num">留痕率</th><th class="num">均时长(h)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in person.persons" :key="p.employeeId">
              <td>{{ p.name }}</td>
              <td>{{ p.deptName || '—' }}</td>
              <td class="num">{{ p.due }}</td>
              <td class="num">{{ p.done }}</td>
              <td class="num">{{ p.open }}</td>
              <td class="num">{{ p.overdue }}</td>
              <td class="num">{{ p.rejected }}</td>
              <td class="num">{{ p.onTimeRate }}%</td>
              <td class="num">{{ p.evidenceRate }}%</td>
              <td class="num">{{ p.avgHours }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">所选区间没有人员绩效数据</div>
      </div>
    </template>

    <div v-else-if="!loading" class="card empty">暂无统计数据，先导入工作日报或建几张工单</div>
    <div v-else class="card empty">加载中…</div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { statsApi, RANGE_PRESETS, rangeOf } from '../api/stats.js'
import { deptApi, employeeApi } from '../api/org.js'
import LineChart from '../components/charts/LineChart.vue'
import BarChart from '../components/charts/BarChart.vue'
import PairBarChart from '../components/charts/PairBarChart.vue'
import DonutChart from '../components/charts/DonutChart.vue'
import ScatterChart from '../components/charts/ScatterChart.vue'
import RadarChart from '../components/charts/RadarChart.vue'
import FunnelChart from '../components/charts/FunnelChart.vue'
import HeatmapChart from '../components/charts/HeatmapChart.vue'

const router = useRouter()
const preset = ref('month')
const from = ref('')
const to = ref('')
const deptId = ref(null)
const employeeId = ref(null)
const overview = ref(null)
const process = ref(null)
const person = ref(null)
const deptOptions = ref([])
const employeeOptions = ref([])
const loading = ref(false)
const error = ref('')

const trendLabels = computed(() => (overview.value?.trend || []).map((p) => p.date.slice(5)))
const trendSeries = computed(() => [
  { label: '新增', values: (overview.value?.trend || []).map((p) => p.created) },
  { label: '完成', values: (overview.value?.trend || []).map((p) => p.done), color: 'var(--ok)' },
  { label: '在办', values: (overview.value?.trend || []).map((p) => p.open), color: 'var(--warn)' }
])

const deptBars = computed(() =>
  (overview.value?.depts || []).map((d) => ({
    label: d.name || '未分配',
    value: d.completionRate,
    color: d.completionRate >= 90 ? 'var(--ok)' : d.completionRate >= 60 ? 'var(--info)' : 'var(--warn)'
  }))
)

const scatterPoints = computed(() =>
  (person.value?.persons || []).map((p) => ({
    x: p.done,
    y: p.overdue,
    size: p.done + p.open,
    rate: p.onTimeRate,
    label: p.name
  }))
)

// 后端切片与漏斗用 count，图表组件统一读 value，这里转一次，免得每个组件各写一套字段兼容
const toItems = (list) => (list || []).map((s) => ({ label: s.label, value: s.count }))
const statusItems = computed(() => toItems(overview.value?.status))
const overdueBinItems = computed(() => toItems(overview.value?.overdueBins))
const recurrentItems = computed(() => toItems(overview.value?.recurrent))
const typeItems = computed(() => toItems(overview.value?.types))
const funnelSteps = computed(() => toItems(person.value?.funnel))
const heatCells = computed(() =>
  (overview.value?.heatmap || []).map((c) => ({ row: c.row, column: c.column, value: c.count }))
)

function formatKpi(kpi) {
  const value = Number(kpi.value) || 0
  if (kpi.unit === '%') return value.toFixed(1)
  if (kpi.unit === 'h') return value.toFixed(1)
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

function kpiTone(kpi) {
  const value = Number(kpi.value) || 0
  if (kpi.code === 'overdue_rate' || kpi.code === 'recurrence_rate') {
    return value > 20 ? 'kpi-bad' : 'kpi-ok'
  }
  if (kpi.unit === '%') {
    if (value >= 90) return 'kpi-ok'
    if (value < 60) return 'kpi-warn'
  }
  return ''
}

function pickPreset(item) {
  preset.value = item.key
  if (item.days === null) return
  const range = rangeOf(item)
  from.value = range.from
  to.value = range.to
  load()
}

function openTask(item) {
  if (item?.id) router.push({ name: 'task-detail', params: { id: item.id } })
}

async function load() {
  if (!from.value || !to.value) return
  loading.value = true
  error.value = ''
  const params = { from: from.value, to: to.value, deptId: deptId.value || undefined }
  try {
    overview.value = await statsApi.overview(params)
  } catch (e) {
    error.value = e.message
    overview.value = null
  }
  try {
    process.value = await statsApi.process({ from: from.value, to: to.value })
  } catch (e) {
    process.value = null
  }
  try {
    person.value = await statsApi.person({ ...params, employeeId: employeeId.value || undefined })
  } catch (e) {
    person.value = null
  }
  loading.value = false
}

async function loadRefs() {
  try {
    const tree = await deptApi.tree()
    deptOptions.value = flatten(tree)
  } catch (e) {
    deptOptions.value = []
  }
  try {
    employeeOptions.value = await employeeApi.options()
  } catch (e) {
    employeeOptions.value = []
  }
}

function flatten(nodes, level = 1) {
  const list = []
  for (const node of nodes || []) {
    list.push({ id: node.id, name: node.name, level })
    list.push(...flatten(node.children, level + 1))
  }
  return list
}

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

.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.kpi {
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  padding: 12px 14px;
}

.kpi-label {
  color: var(--txt-2);
  font-size: 12px;
}

.kpi-value {
  font-size: 22px;
  font-weight: 600;
  margin: 4px 0 2px;
}

.kpi-value .unit {
  font-size: 12px;
  color: var(--txt-3);
  margin-left: 3px;
}

.kpi-formula {
  color: var(--txt-3);
  font-size: 11px;
  line-height: 1.4;
}

.kpi-ok .kpi-value { color: var(--ok); }
.kpi-warn .kpi-value { color: var(--warn); }
.kpi-bad .kpi-value { color: var(--bad); }

.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.chart-card {
  padding: 14px 16px 16px;
}

.chart-card.wide {
  grid-column: span 2;
}

.chart-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.chart-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.block {
  margin-bottom: 12px;
}

.person-block {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.top-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 240px;
  overflow-y: auto;
}

.top-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  text-align: left;
  cursor: pointer;
}

.top-item:hover {
  background: #F6F7F8;
}

.top-title {
  font-size: 13px;
  color: var(--txt);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--txt-3);
}

@media (max-width: 960px) {
  .chart-card.wide {
    grid-column: span 1;
  }
}
</style>
