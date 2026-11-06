<template>
  <div class="export-panel" :class="{ card: !compact, compact }">
    <div v-if="!compact" class="panel-head">
      <h3>{{ title }}</h3>
      <span class="muted">异步任务，产物进度回填在这里，关掉浏览器也不丢</span>
    </div>
    <div class="panel-form">
      <select v-if="pickable.length > 1" v-model="template" style="min-width: 92px">
        <option v-for="item in pickable" :key="item.code" :value="item.code">{{ item.label }}</option>
      </select>
      <template v-if="!followsPageRange">
        <input v-model="from" type="date" style="width: 132px" />
        <span class="muted">至</span>
        <input v-model="to" type="date" style="width: 132px" />
      </template>
      <button class="btn btn-primary" :disabled="running" title="台账按当前列表筛选缩行" @click="run">
        {{ running ? '导出中…' : '开始导出' }}
      </button>
      <slot name="after-export" />
      <span v-if="!compact" class="muted">{{ hint }}</span>
    </div>
    <div v-if="!compact && template === 'LEDGER'" class="filter-line">
      <span class="filter-label">综合筛选</span>
      <template v-if="filterChips.length">
        <span v-for="chip in filterChips" :key="chip" class="filter-chip">{{ chip }}</span>
        <span class="muted">台账按这些条件缩行；日期区间限定创建或计划完成时间</span>
      </template>
      <span v-else class="muted">未设置：导出区间内全部工单；列表页筛选条的条件会实时带到这里</span>
    </div>
    <div v-else-if="!compact" class="filter-line">
      <span class="muted">日报按日期区间与汇报人导出，不使用列表页的筛选条件</span>
    </div>
    <div v-if="error" class="form-error">{{ error }}</div>
    <div v-if="job" class="job-line">
      <span class="tag" :class="JOB_STATUS_CLASS[job.status]">{{ job.statusLabel }}</span>
      <span class="job-text">
        {{ job.jobTypeLabel }} · {{ job.createdAt || '' }}
        <template v-if="job.status === 'FAILED'"> · {{ job.lastError }}</template>
      </span>
      <span class="job-progress num">{{ job.progress }}%</span>
      <button v-if="file" class="btn-link" @click="download">下载导出件</button>
    </div>
    <div v-if="job" class="job-bar"><span class="job-fill" :style="{ width: job.progress + '%' }"></span></div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { exportApi, EXPORT_TEMPLATES } from '../api/export.js'
import { JOB_STATUS_CLASS, waitJob } from '../api/admin.js'

const props = defineProps({
  codes: { type: Array, default: () => ['DAILY'] },
  title: { type: String, default: '导出' },
  /** 紧凑形态：不占一张卡，只出模板、区间与按钮，供页面把导出并进筛选条 */
  compact: { type: Boolean, default: false },
  /** 列表页那套综合筛选：台账模板按它缩行（DESIGN 4.6） */
  filters: { type: Object, default: () => ({}) },
  filterChips: { type: Array, default: () => [] },
  /** 与页面筛选条联动：统计看板等页面把当前区间与部门带进来，导出默认跟着页面走 */
  defaultFrom: { type: String, default: '' },
  defaultTo: { type: String, default: '' },
  deptId: { type: [Number, String], default: null }
})

const pickable = computed(() => EXPORT_TEMPLATES.filter((t) => props.codes.includes(t.code)))
const template = ref(props.codes[0] || 'DAILY')
const from = ref('')
const to = ref('')
const running = ref(false)
const error = ref('')
const job = ref(null)
const file = ref(null)

const hint = computed(() => (pickable.value.find((t) => t.code === template.value) || {}).desc || '')

/** 页面已带日期区间时，导出直接跟着页面走，不再重复一组日期输入 */
const followsPageRange = computed(() => props.compact && Boolean(props.defaultFrom && props.defaultTo))

function dayString(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

async function run() {
  if (running.value) return
  running.value = true
  error.value = ''
  file.value = null
  try {
    const data = {
      template: template.value,
      from: from.value || undefined,
      to: to.value || undefined
    }
    if (template.value === 'LEDGER') {
      const f = props.filters || {}
      Object.assign(data, {
        keyword: f.keyword || undefined,
        status: f.status || undefined,
        type: f.type || undefined,
        deptId: f.deptId || undefined,
        deptIds: f.deptIds && f.deptIds.length ? f.deptIds : undefined,
        overdueOnly: f.overdueOnly || undefined,
        blockedOnly: f.blockedOnly || undefined,
        mine: f.mine || undefined
      })
    } else if (template.value === 'RATES' && props.deptId) {
      data.deptId = props.deptId
    }
    const created = await exportApi.createAsync(data)
    job.value = created
    const final = await waitJob(created.id)
    job.value = final
    if (final.status === 'FAILED') throw new Error(final.lastError || '导出任务失败')
    const list = await exportApi.list()
    file.value = (list || []).find((item) => item.templateCode === template.value) || null
  } catch (e) {
    error.value = e.message
  } finally {
    running.value = false
  }
}

async function download() {
  if (!file.value) return
  try {
    await exportApi.download(file.value.id, file.value.fileName)
  } catch (e) {
    error.value = e.message
  }
}

onMounted(() => {
  if (props.defaultFrom && props.defaultTo) {
    from.value = props.defaultFrom
    to.value = props.defaultTo
    return
  }
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 6)
  from.value = dayString(start)
  to.value = dayString(end)
})

watch(() => [props.defaultFrom, props.defaultTo], ([start, end]) => {
  if (start && end) {
    from.value = start
    to.value = end
  }
})
</script>

<style scoped>
.export-panel {
  margin-top: 12px;
  padding: 14px 16px;
}

/* 紧凑形态：并入页面的筛选条，不再自带卡片与标题 */
.export-panel.compact {
  margin-top: 0;
  padding: 0;
}

.export-panel.compact .job-line {
  margin-top: 8px;
}

.export-panel.compact .job-bar {
  margin-top: 4px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.panel-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.panel-form {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.muted {
  color: var(--txt-3);
  font-size: 12px;
}

.filter-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #E7E9EC;
}

.filter-label {
  color: var(--txt-2);
  font-size: 12px;
  font-weight: 600;
}

.filter-chip {
  padding: 1px 8px;
  border: 1px solid #DDE1E6;
  border-radius: 10px;
  background: #F7F8FA;
  color: var(--txt-2);
  font-size: 12px;
}

.job-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.job-text {
  flex: 1;
  min-width: 0;
  color: var(--txt-2);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.job-progress {
  color: var(--txt-2);
  font-size: 12px;
}

.job-bar {
  margin-top: 8px;
  height: 4px;
  background: #EFF1F3;
  border-radius: 2px;
  overflow: hidden;
}

.job-fill {
  display: block;
  height: 100%;
  background: var(--txt-2);
}
</style>
