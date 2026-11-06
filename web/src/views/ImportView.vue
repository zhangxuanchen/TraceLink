<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">导入向导</h2>
        <p class="page-sub">日报工作簿智能识别 → 预览校正 → 确认建单；导出统一在工单中心的「导出工单与日报」里做</p>
      </div>
      <div class="head-actions">
        <button class="btn" @click="loadBatches">刷新批次</button>
      </div>
    </div>

    <div class="steps">
      <div v-for="(step, index) in STEPS" :key="step.key" class="step"
           :class="{ active: stepNo === index + 1, done: stepNo > index + 1 }">
        <span class="step-index num">{{ index + 1 }}</span>
        <div>
          <div class="step-title">{{ step.title }}</div>
          <div class="step-desc">{{ step.desc }}</div>
        </div>
      </div>
    </div>

    <div v-if="error" class="form-error">{{ error }}</div>

    <div class="layout">
      <div class="main-col">
        <!-- 第一步：上传 -->
        <div v-if="stepNo === 1" class="card block">
          <div class="block-head"><h3>选择工作簿</h3><span class="muted">支持 .xlsx，单文件最大 100 MB</span></div>
          <div class="dropzone" :class="{ dragging }"
               @dragover.prevent="dragging = true"
               @dragleave.prevent="dragging = false"
               @drop.prevent="onDrop">
            <p class="drop-title">把《工作日志》拖到这里，或</p>
            <label class="btn btn-primary file-btn">
              选择文件
              <input type="file" accept=".xlsx" hidden @change="onPick" />
            </label>
            <p class="drop-hint">同一个文件重复上传会直接复用旧批次，不会重复建单</p>
          </div>
          <div v-if="uploading" class="uploading">正在上传与解析，大文件需要 1 分钟左右…</div>
          <div v-if="batch" class="upload-result">
            <span class="tag tag-ok">已上传 · 批次 #{{ batch.id }}</span>
            <span class="muted">{{ batch.fileName }}</span>
            <span v-if="batch.duplicate" class="tag tag-warn">内容与历史批次一致，已复用</span>
          </div>
        </div>

        <!-- 第二步：预览校正 -->
        <template v-if="stepNo === 2 && preview">
          <div class="card block">
            <div class="block-head">
              <h3>识别率与页摘要</h3>
              <div class="head-actions">
                <button class="btn" @click="loadPreview(true)">重新解析</button>
              </div>
            </div>
            <div class="recognition">
              <div class="rec-item" :class="preview.recognition.rowPass ? 'ok' : 'bad'">
                <span class="rec-label">明细行识别率</span>
                <span class="rec-value num">{{ percent(preview.recognition.rowRate) }}</span>
                <span class="muted">目标 {{ percent(preview.recognition.rowTarget) }} ·
                  有责任人 {{ preview.recognition.rowsWithAssignee }} / {{ preview.recognition.detailRows }}</span>
              </div>
              <div class="rec-item" :class="preview.recognition.personPass ? 'ok' : 'bad'">
                <span class="rec-label">人名匹配率</span>
                <span class="rec-value num">{{ percent(preview.recognition.personRate) }}</span>
                <span class="muted">目标 {{ percent(preview.recognition.personTarget) }} ·
                  命中 {{ preview.recognition.personMatched }} / {{ preview.recognition.personMentions }}</span>
              </div>
              <div class="rec-item">
                <span class="rec-label">识别页 / 明细行</span>
                <span class="rec-value num">{{ activeSheets.length }} / {{ preview.recognition.detailRows }}</span>
                <span class="muted">生产指标 {{ preview.metrics?.length || 0 }} 条</span>
              </div>
            </div>
            <div class="sheets">
              <span v-for="sheet in preview.sheets" :key="sheet.name" class="sheet-chip"
                    :class="{ empty: sheet.empty }">
                {{ sheet.name }}
                <b v-if="!sheet.empty" class="num">{{ sheet.rowCount }}</b>
                <span v-else>空</span>
              </span>
            </div>
          </div>

          <div class="card block">
            <div class="block-head">
              <h3>明细行校正</h3>
              <div class="toolbar inline">
                <input v-model="keyword" placeholder="搜索标题或人名" style="width: 180px" />
                <label class="check"><input v-model="onlyIssues" type="checkbox" /> 只看需处理</label>
                <span class="muted">共 {{ filteredRows.length }} 行</span>
              </div>
            </div>
            <table class="table">
              <thead>
                <tr>
                  <th style="width: 96px">页 / 行</th>
                  <th style="width: 64px">级别</th>
                  <th>工作内容</th>
                  <th style="width: 110px">责任人</th>
                  <th style="width: 104px">日期</th>
                  <th style="width: 84px">动作</th>
                  <th style="width: 210px">提示</th>
                  <th style="width: 64px">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in filteredRows" :key="row.id">
                  <td class="muted small">{{ row.sheet }} · R{{ row.rowNo }}</td>
                  <td><span class="tag" :class="levelClass(row.level)">{{ row.levelLabel }}</span></td>
                  <td>
                    <div class="row-title">{{ row.title || '（空）' }}</div>
                    <div class="muted small">{{ row.problemText || row.resultText || '—' }}</div>
                  </td>
                  <td>{{ row.assigneeName || row.assigneeText || '待认领' }}</td>
                  <td class="num small">{{ row.reportDate || '—' }}</td>
                  <td>
                    <span class="tag" :class="actionClass(row.action)">{{ row.actionLabel }}</span>
                    <div v-if="row.mergeTaskNo" class="muted small">{{ row.mergeTaskNo }}</div>
                  </td>
                  <td class="small muted">{{ (row.issues || []).join('；') || '—' }}</td>
                  <td><button class="btn-link" @click="openEdit(row)">校正</button></td>
                </tr>
              </tbody>
            </table>
            <div v-if="!filteredRows.length" class="empty">没有符合条件的行</div>
          </div>

          <div class="card block actions-bar">
            <div class="summary">
              将新建 <b class="num">{{ countBy('CREATE') }}</b> 单，并入
              <b class="num">{{ countBy('MERGE') }}</b> 条追踪，跳过
              <b class="num">{{ countBy('SKIP') }}</b> 行
            </div>
            <div class="head-actions">
              <button class="btn" @click="downloadReport">下载校验报告</button>
              <button class="btn btn-primary" :disabled="confirming" @click="confirmImport">
                {{ confirming ? '入库中…' : '确认入库' }}
              </button>
            </div>
          </div>
        </template>

        <!-- 第三步：结果 -->
        <div v-if="stepNo === 3 && result" class="card block">
          <div class="block-head"><h3>入库完成</h3><span class="muted">批次 #{{ result.batchId }}</span></div>
          <div class="result-grid">
            <div class="result-item">
              <span class="result-label">新建工单</span>
              <span class="result-value num">{{ result.created }}</span>
            </div>
            <div class="result-item">
              <span class="result-label">并入追踪</span>
              <span class="result-value num">{{ result.merged }}</span>
            </div>
            <div class="result-item">
              <span class="result-label">跳过</span>
              <span class="result-value num">{{ result.skipped }}</span>
            </div>
            <div class="result-item" :class="{ bad: result.failed > 0 }">
              <span class="result-label">失败</span>
              <span class="result-value num">{{ result.failed }}</span>
            </div>
          </div>
          <p class="result-message">{{ result.message }}</p>
          <div class="head-actions">
            <button class="btn" @click="downloadReport">下载校验报告</button>
            <router-link class="btn btn-primary" to="/tasks">去工单中心</router-link>
            <button class="btn" @click="resetWizard">再导入一份</button>
          </div>
        </div>

        <!-- 导出改到工单中心做，这里只保留导入链路 -->
      </div>

      <aside class="side-col">
        <div class="card">
          <div class="block-head"><h3>批次历史</h3><span class="muted">{{ batches.length }} 条</span></div>
          <div v-if="!batches.length" class="empty">还没有导入批次</div>
          <button v-for="item in batches" :key="item.id" class="batch"
                  :class="{ active: batch?.id === item.id }" @click="openBatch(item)">
            <div class="batch-top">
              <span class="batch-name" :title="item.fileName">{{ item.fileName }}</span>
              <span class="tag" :class="batchClass(item.status)">{{ item.statusLabel }}</span>
            </div>
            <div class="batch-meta">
              <span>#{{ item.id }}</span>
              <span>{{ item.createdAt }}</span>
            </div>
            <div class="batch-counts">
              <span>共 {{ item.rowTotal || 0 }}</span>
              <span class="ok">通过 {{ item.rowOk || 0 }}</span>
              <span class="warn">警告 {{ item.rowWarn || 0 }}</span>
              <span v-if="item.rowError" class="bad">错误 {{ item.rowError }}</span>
            </div>
            <div v-if="item.errorMsg" class="batch-error">{{ item.errorMsg }}</div>
          </button>
        </div>
      </aside>
    </div>

    <BaseModal v-if="editing" title="校正导入行" @close="editing = null">
      <div class="field">
        <label>工作内容</label>
        <input v-model="editForm.title" placeholder="2 到 120 字" />
      </div>
      <div class="grid-2">
        <div class="field">
          <label>责任人</label>
          <select v-model="editForm.assigneeId">
            <option value="">待认领</option>
            <option v-for="e in employeeOptions" :key="e.id" :value="String(e.id)">{{ e.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>计划完成</label>
          <input v-model="editForm.planEnd" type="date" />
        </div>
      </div>
      <div class="field">
        <label>动作</label>
        <select v-model="editForm.action">
          <option value="CREATE">新建工单</option>
          <option value="MERGE">并入已有工单</option>
          <option value="SKIP">跳过本行</option>
        </select>
      </div>
      <div class="field">
        <label>汇报结果</label>
        <textarea v-model="editForm.resultText" rows="3" placeholder="填了结果就按已完成建单"></textarea>
      </div>
      <div class="field">
        <label>遇到的问题</label>
        <textarea v-model="editForm.problemText" rows="2"></textarea>
      </div>
      <template #footer>
        <div v-if="editError" class="form-error edit-error">{{ editError }}</div>
        <button class="btn" @click="editing = null">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="saveRow">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { importApi, downloadFile } from '../api/import.js'
import { employeeApi } from '../api/org.js'
import BaseModal from '../components/BaseModal.vue'

const route = useRoute()

const STEPS = [
  { key: 'upload', title: '上传工作簿', desc: '内容哈希幂等，同文件不重复解析' },
  { key: 'preview', title: '预览校正', desc: '识别率、页摘要、逐行改责任人' },
  { key: 'confirm', title: '确认入库', desc: '建单或并入追踪，生成校验报告' }
]

const stepNo = ref(1)
const batch = ref(null)
const preview = ref(null)
const result = ref(null)
const batches = ref([])
const employeeOptions = ref([])
const keyword = ref('')
const onlyIssues = ref(true)
const dragging = ref(false)
const uploading = ref(false)
const confirming = ref(false)
const error = ref('')
const editing = ref(null)
const editError = ref('')
const saving = ref(false)
const editForm = ref({ title: '', assigneeId: '', planEnd: '', action: 'CREATE', resultText: '', problemText: '' })

const activeSheets = computed(() => (preview.value?.sheets || []).filter((s) => !s.empty))

const filteredRows = computed(() => {
  const rows = preview.value?.rows || []
  const kw = keyword.value.trim()
  return rows.filter((row) => {
    if (onlyIssues.value && row.level !== 'WARN' && row.level !== 'ERROR') return false
    if (!kw) return true
    return (row.title || '').includes(kw)
      || (row.assigneeName || row.assigneeText || '').includes(kw)
      || (row.reporterName || '').includes(kw)
  })
})

function percent(value) {
  return `${(Number(value) * 100).toFixed(1)}%`
}

function levelClass(level) {
  return level === 'OK' ? 'tag-ok' : level === 'WARN' ? 'tag-warn' : 'tag-bad'
}

function actionClass(action) {
  return action === 'MERGE' ? 'tag-warn' : action === 'SKIP' ? 'tag-bad' : 'tag-ok'
}

function batchClass(status) {
  if (status === 'CONFIRMED') return 'tag-ok'
  if (status === 'PREVIEWED') return 'tag-warn'
  if (status === 'FAILED' || status === 'REVOKED') return 'tag-bad'
  return ''
}

function countBy(action) {
  return (preview.value?.rows || []).filter((r) => r.action === action).length
}

async function loadBatches() {
  try {
    batches.value = await importApi.list()
  } catch (e) {
    error.value = e.message
  }
}

async function loadEmployees() {
  try {
    employeeOptions.value = await employeeApi.options()
  } catch (e) {
    employeeOptions.value = []
  }
}

function onDrop(event) {
  dragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) upload(file)
}

function onPick(event) {
  const file = event.target.files?.[0]
  if (file) upload(file)
  event.target.value = ''
}

async function upload(file) {
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    error.value = '只支持 .xlsx 工作簿'
    return
  }
  uploading.value = true
  error.value = ''
  result.value = null
  preview.value = null
  try {
    batch.value = await importApi.upload(file)
    stepNo.value = 2
    await loadBatches()
    await loadPreview()
  } catch (e) {
    error.value = e.message
  } finally {
    uploading.value = false
  }
}

async function loadPreview(reanalyze = false) {
  if (!batch.value) return
  error.value = ''
  try {
    preview.value = await importApi.preview(batch.value.id, reanalyze)
  } catch (e) {
    error.value = e.message
    preview.value = null
  }
}

async function openBatch(item) {
  batch.value = item
  result.value = null
  if (item.status === 'CONFIRMED') {
    error.value = ''
    try {
      preview.value = await importApi.preview(item.id)
      stepNo.value = 2
    } catch (e) {
      error.value = e.message
    }
    return
  }
  preview.value = null
  stepNo.value = 1
  await loadPreview()
  stepNo.value = 2
}

function openEdit(row) {
  editing.value = row
  editError.value = ''
  editForm.value = {
    title: row.title || '',
    assigneeId: row.assigneeId ? String(row.assigneeId) : '',
    planEnd: row.planEnd || '',
    action: row.action || 'CREATE',
    resultText: row.resultText || '',
    problemText: row.problemText || ''
  }
}

async function saveRow() {
  if (!editing.value || !batch.value) return
  saving.value = true
  editError.value = ''
  try {
    await importApi.updateRow(batch.value.id, editing.value.id, {
      title: editForm.value.title,
      assigneeId: editForm.value.assigneeId || null,
      deptId: null,
      planEnd: editForm.value.planEnd || null,
      action: editForm.value.action,
      resultText: editForm.value.resultText,
      problemText: editForm.value.problemText,
      solutionText: null
    })
    editing.value = null
    await loadPreview()
    await loadBatches()
  } catch (e) {
    editError.value = e.message
  } finally {
    saving.value = false
  }
}

async function confirmImport() {
  if (!batch.value) return
  confirming.value = true
  error.value = ''
  try {
    result.value = await importApi.confirm(batch.value.id)
    stepNo.value = 3
    await loadBatches()
  } catch (e) {
    error.value = e.message
  } finally {
    confirming.value = false
  }
}

async function downloadReport() {
  if (!batch.value) return
  try {
    await downloadFile(importApi.reportUrl(batch.value.id), `导入报告-${batch.value.id}.xlsx`)
  } catch (e) {
    error.value = e.message
  }
}

function resetWizard() {
  stepNo.value = 1
  batch.value = null
  preview.value = null
  result.value = null
  error.value = ''
}

onMounted(async () => {
  await Promise.all([loadBatches(), loadEmployees()])
  const batchId = Number(route.query.batch)
  if (batchId) {
    const found = batches.value.find((item) => Number(item.id) === batchId)
    await openBatch(found || { id: batchId })
  }
})
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.step {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  padding: 10px 14px;
}

.step.active {
  border-color: var(--txt);
}

.step.done {
  border-color: rgba(46, 125, 50, 0.4);
}

.step-index {
  width: 22px;
  height: 22px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  font-size: 12px;
  background: #F1F2F4;
  color: var(--txt-2);
  flex: none;
}

.step.active .step-index {
  background: var(--txt);
  color: #fff;
}

.step.done .step-index {
  background: rgba(46, 125, 50, 0.12);
  color: var(--ok);
}

.step-title {
  font-size: 13px;
  font-weight: 600;
}

.step-desc {
  font-size: 12px;
  color: var(--txt-3);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 12px;
}

.main-col {
  min-width: 0;
}

.side-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.block {
  margin-bottom: 12px;
  padding: 14px 16px 16px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.block-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.muted {
  color: var(--txt-3);
  font-size: 12px;
}

.small {
  font-size: 12px;
}

.dropzone {
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
  padding: 28px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  transition: border-color 120ms ease, background 120ms ease;
}

.dropzone.dragging {
  border-color: var(--info);
  background: rgba(10, 108, 255, 0.04);
}

.drop-title {
  margin: 0;
  font-size: 14px;
}

.file-btn {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.drop-hint {
  margin: 0;
  color: var(--txt-3);
  font-size: 12px;
}

.uploading {
  margin-top: 10px;
  color: var(--txt-2);
  font-size: 13px;
}

.upload-result {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.recognition {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.rec-item {
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rec-item.ok {
  border-color: rgba(46, 125, 50, 0.28);
  background: rgba(46, 125, 50, 0.04);
}

.rec-item.bad {
  border-color: rgba(198, 40, 40, 0.28);
  background: rgba(198, 40, 40, 0.04);
}

.rec-label {
  font-size: 12px;
  color: var(--txt-2);
}

.rec-value {
  font-size: 20px;
  font-weight: 600;
}

.sheets {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.sheet-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 12px;
  background: #F6F7F8;
  font-size: 12px;
  color: var(--txt-2);
}

.sheet-chip.empty {
  color: var(--txt-3);
}

.toolbar.inline {
  margin: 0;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--txt-2);
}

.row-title {
  font-size: 13px;
}

.actions-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary {
  font-size: 13px;
  color: var(--txt-2);
}

.summary b {
  color: var(--txt);
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 10px;
}

.result-item {
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}

.result-item.bad {
  border-color: rgba(198, 40, 40, 0.28);
  color: var(--bad);
}

.result-label {
  font-size: 12px;
  color: var(--txt-2);
  display: block;
}

.result-value {
  font-size: 20px;
  font-weight: 600;
}

.result-message {
  color: var(--txt-2);
  font-size: 13px;
  margin: 0 0 12px;
}

.batch {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-top: 1px solid var(--divider);
  background: none;
  text-align: left;
  cursor: pointer;
}

.batch:first-of-type {
  border-top: none;
}

.batch:hover,
.batch.active {
  background: #F8F9FA;
}

.batch-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.batch-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-meta,
.batch-counts {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--txt-3);
}

.batch-counts .ok { color: var(--ok); }
.batch-counts .warn { color: var(--warn); }
.batch-counts .bad { color: var(--bad); }

.batch-error {
  font-size: 11px;
  color: var(--bad);
  overflow: hidden;
  text-overflow: ellipsis;
}

.edit-error {
  margin: 0 auto 0 0;
}

@media (max-width: 1024px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .steps {
    grid-template-columns: 1fr;
  }
}
</style>
