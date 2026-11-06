<template>
  <section>
    <div class="counts">
      <button class="count-chip" :class="{ active: isOnlyStatus('PENDING') }" @click="focusStatus('PENDING')">
        <span class="count-label">待接单</span>
        <span class="count-value num">{{ counts.pending }}</span>
      </button>
      <button class="count-chip" :class="{ active: isOnlyStatus('IN_PROGRESS') }" @click="focusStatus('IN_PROGRESS')">
        <span class="count-label">进行中</span>
        <span class="count-value num">{{ counts.inProgress }}</span>
      </button>
      <button class="count-chip" :class="{ active: isOnlyStatus('BLOCKED') }" @click="focusStatus('BLOCKED')">
        <span class="count-label">受阻</span>
        <span class="count-value num">{{ counts.blocked }}</span>
      </button>
      <button class="count-chip" :class="{ active: isOnlyStatus('SUBMITTED') }" @click="focusStatus('SUBMITTED')">
        <span class="count-label">待验收</span>
        <span class="count-value num">{{ counts.submitted }}</span>
      </button>
      <button class="count-chip" :class="{ active: isOnlyStatus('DONE') }" @click="focusStatus('DONE')">
        <span class="count-label">已验收</span>
        <span class="count-value num">{{ counts.done }}</span>
      </button>
      <button class="count-chip count-danger" :class="{ active: filters.overdue }" @click="focusOverdue">
        <span class="count-label">逾期</span>
        <span class="count-value num">{{ counts.overdue }}</span>
      </button>
      <button class="count-chip" :class="{ active: filters.mine }" @click="focusMine">
        <span class="count-label">与我相关</span>
        <span class="count-value num">{{ counts.mine }}</span>
      </button>
    </div>

    <div class="toolbar">
      <input v-model="filters.keyword" placeholder="工单号或标题" style="width: 180px" @keyup.enter="search" />
      <MultiSelect v-model="filters.status" :options="statusOptions" placeholder="全部状态" @change="search" />
      <MultiSelect v-model="filters.type" :options="typeOptions" placeholder="全部类型" @change="search" />
      <MultiSelect v-model="filters.deptId" :options="deptSelectOptions" placeholder="全部部门" @change="search" />
      <button class="btn" @click="search">查询</button>
      <button class="btn" @click="reset">重置</button>
      <span class="count">共 {{ page.total }} 张</span>

      <!-- 导出并进筛选条：模板、区间、开始导出，右侧紧挨「新建工单」 -->
      <ExportPanel v-if="canPrivileged" compact class="toolbar-export"
                   :codes="['LEDGER', 'DAILY']" :filters="exportFilters">
        <template #after-export>
          <button class="btn btn-primary" @click="openCreate">新建工单</button>
        </template>
      </ExportPanel>
      <div v-else class="create-row">
        <button class="btn btn-primary" @click="openCreate">新建工单</button>
      </div>
    </div>

    <div v-if="error" class="form-error">{{ error }}</div>

    <div class="card">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!page.records.length" class="empty">
        没有符合条件的工单，换个筛选条件或新建一张
      </div>
      <table v-else class="table">
        <thead>
          <tr>
            <th>工单号</th>
            <th>标题</th>
            <th>类型</th>
            <th>承办部门</th>
            <th>主责</th>
            <th>负责人</th>
            <th>期限</th>
            <th class="num">进度</th>
            <th>状态</th>
            <th style="width: 60px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in page.records" :key="row.id" class="row" @click="openDetail(row)">
            <td class="num">{{ row.taskNo }}</td>
            <td>
              <div class="title-line">
                <span v-if="row.priority === 0" class="tag tag-bad">紧急</span>
                <span class="title">{{ row.title }}</span>
              </div>
              <div class="sub-line">
                <span v-if="row.recurrenceOf" class="tag tag-warn">复发</span>
                <span v-if="row.overdue" class="tag tag-bad">{{ row.overdueLevelLabel }} · {{ row.overdueDays }} 天</span>
                <span v-if="row.rejectCount" class="muted">退回 {{ row.rejectCount }} 次</span>
                <span v-if="row.delayCount" class="muted">改期 {{ row.delayCount }} 次</span>
              </div>
            </td>
            <td>{{ row.typeLabel }}</td>
            <td>{{ row.deptName || '—' }}</td>
            <td>{{ row.primaryAssigneeName || row.assigneeText || '待认领' }}</td>
            <td>{{ row.ownerName || '—' }}</td>
            <td class="num">{{ formatDateTime(row.planEnd) }}</td>
            <td class="num">{{ row.progress }}%</td>
            <td>
              <span class="tag" :class="statusClass(row.status)">{{ row.statusLabel }}</span>
            </td>
            <td>
              <button class="btn-link" @click.stop="openDetail(row)">查看</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="page.total > page.pageSize" class="pager">
      <button class="btn" :disabled="page.pageNo <= 1" @click="go(page.pageNo - 1)">上一页</button>
      <span class="num">{{ page.pageNo }} / {{ Math.ceil(page.total / page.pageSize) }}</span>
      <button class="btn" :disabled="page.pageNo * page.pageSize >= page.total" @click="go(page.pageNo + 1)">下一页</button>
    </div>

    <BaseModal v-if="dialog.open" title="新建工单" @close="dialog.open = false">
      <div v-if="dialog.error" class="form-error">{{ dialog.error }}</div>
      <div class="field">
        <label>标题<span class="req">*</span></label>
        <input v-model="dialog.form.title" :disabled="dialog.saving" placeholder="如 生产部冲压工位毛刺超标整改" />
      </div>
      <div class="grid-2">
        <div class="field">
          <label>工单类型<span class="req">*</span></label>
          <select v-model="dialog.form.type" :disabled="dialog.saving">
            <option value="" disabled>请选择</option>
            <option v-for="t in TASK_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>
        <div class="field">
          <label>优先级</label>
          <select v-model.number="dialog.form.priority" :disabled="dialog.saving">
            <option v-for="p in PRIORITIES" :key="p.value" :value="p.value">{{ p.label }}</option>
          </select>
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>承办部门<span class="req">*</span></label>
          <select v-model="dialog.form.deptId" :disabled="dialog.saving">
            <option :value="null" disabled>请选择</option>
            <option v-for="d in deptOptions" :key="d.id" :value="d.id">
              {{ '　'.repeat(d.level - 1) + d.name }}
            </option>
          </select>
        </div>
        <div class="field">
          <label>完成期限<span class="req">*</span></label>
          <input v-model="dialog.form.planEnd" type="datetime-local" :disabled="dialog.saving" />
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>负责人<span class="req">*</span></label>
          <select v-model="dialog.form.ownerId" :disabled="dialog.saving">
            <option :value="null" disabled>请选择</option>
            <option v-for="e in employees" :key="e.id" :value="e.id">{{ e.name }}（{{ e.deptName }}）</option>
          </select>
        </div>
        <div class="field">
          <label>主责执行人<span class="req">*</span></label>
          <select v-model="dialog.form.primaryAssigneeId" :disabled="dialog.saving">
            <option :value="null" disabled>请选择</option>
            <option v-for="e in employees" :key="e.id" :value="e.id">{{ e.name }}（{{ e.deptName }}）</option>
          </select>
        </div>
      </div>
      <p class="hint">负责人须是承办部门或其上级，主责执行人须在承办部门或其下级；两者不能是同一人。</p>
      <div class="field">
        <label>背景与要求</label>
        <textarea v-model="dialog.form.description" rows="3" :disabled="dialog.saving"
                  placeholder="交代背景、涉及范围与必须达成的结果"></textarea>
      </div>
      <div class="field">
        <label>验收条件（一条一行，最多 20 条）</label>
        <textarea v-model="dialog.form.acceptCriteria" rows="3" :disabled="dialog.saving"
                  placeholder="如 毛刺高度不超过0.05毫米"></textarea>
      </div>
      <div class="field">
        <label>问题项（一条一行，可留空）</label>
        <textarea v-model="dialog.form.issueList" rows="2" :disabled="dialog.saving"
                  placeholder="如 冲压模刀口磨损"></textarea>
      </div>
      <div class="field">
        <label>措施项（一条一行，可留空，负责人默认主责）</label>
        <textarea v-model="dialog.form.actionList" rows="2" :disabled="dialog.saving"
                  placeholder="如 更换冲压模刀口并试模"></textarea>
      </div>
      <template #footer>
        <button class="btn" :disabled="dialog.saving" @click="dialog.open = false">取消</button>
        <button class="btn btn-primary" :disabled="dialog.saving" @click="save">
          {{ dialog.saving ? '提交中…' : '建单' }}
        </button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseModal from '../components/BaseModal.vue'
import ExportPanel from '../components/ExportPanel.vue'
import MultiSelect from '../components/MultiSelect.vue'
import { deptApi, employeeApi } from '../api/org.js'
import { USER_KEY } from '../api/http.js'
import { taskApi } from '../api/task.js'
import {
  PRIORITIES,
  TASK_STATUSES,
  TASK_TYPES,
  formatDateTime,
  splitLines,
  statusClass,
  toApiDateTime
} from '../api/taskOptions.js'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const error = ref('')
const depts = ref([])
const employees = ref([])
const counts = ref({ pending: 0, inProgress: 0, blocked: 0, submitted: 0, done: 0, overdue: 0, mine: 0 })
const page = ref({ total: 0, pageNo: 1, pageSize: 20, records: [] })

const filters = reactive({
  keyword: '', status: [], type: [], deptId: [], overdue: false, mine: false
})
const dialog = reactive({ open: false, saving: false, error: '', form: emptyForm() })

/** 看板视图与导出台账对同一批角色开放（DESIGN 9.2 的按角色可见范围） */
const canPrivileged = computed(() => {
  let roles = []
  try {
    roles = JSON.parse(localStorage.getItem(USER_KEY) || '{}').roles || []
  } catch {
    roles = []
  }
  return roles.some((role) => ['SYS_ADMIN', 'MANAGER', 'OWNER'].includes(role))
})

/** 多选值统一拼成逗号串交给接口：状态 / 类型 / 部门都支持一次选多个 */
function csv(list) {
  return list.length ? list.join(',') : undefined
}

const statusOptions = TASK_STATUSES.map((item) => ({ value: item.value, label: item.label }))
const typeOptions = TASK_TYPES.map((item) => ({ value: item.value, label: item.label }))

/** 导出的综合筛选：列表页筛选条那套条件原样带进导出控件，台账模板按它缩行 */
const exportFilters = computed(() => ({
  keyword: filters.keyword.trim(),
  status: csv(filters.status),
  type: csv(filters.type),
  deptIds: filters.deptId.length ? [...filters.deptId] : undefined,
  overdueOnly: filters.overdue,
  mine: filters.mine
}))

const deptOptions = computed(() => flatten(depts.value))

const deptSelectOptions = computed(() => deptOptions.value.map((dept) => ({
  value: dept.id,
  label: '　'.repeat(dept.level - 1) + dept.name
})))

function emptyForm() {
  return {
    title: '', type: '', deptId: null, ownerId: null, primaryAssigneeId: null, priority: 2,
    planEnd: '', description: '', acceptCriteria: '', issueList: '', actionList: ''
  }
}

function flatten(nodes, out = []) {
  for (const node of nodes) {
    out.push(node)
    if (node.children?.length) flatten(node.children, out)
  }
  return out
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (!depts.value.length) {
      const [treeData, employeeData] = await Promise.all([deptApi.tree(), employeeApi.options()])
      depts.value = treeData || []
      employees.value = employeeData || []
    }
    const [pageData, countData] = await Promise.all([
      taskApi.page({
        keyword: filters.keyword || undefined,
        status: csv(filters.status),
        type: csv(filters.type),
        deptId: csv(filters.deptId),
        overdue: filters.overdue || undefined,
        mine: filters.mine || undefined,
        pageNo: page.value.pageNo,
        pageSize: page.value.pageSize
      }),
      taskApi.counts()
    ])
    page.value = pageData
    counts.value = countData
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function search() {
  page.value.pageNo = 1
  load()
}

function reset() {
  filters.keyword = ''
  filters.status = []
  filters.type = []
  filters.deptId = []
  filters.overdue = false
  filters.mine = false
  search()
}

/** 角标是快捷筛选：点一次只看这一个状态，再点一次取消 */
function isOnlyStatus(status) {
  return filters.status.length === 1 && filters.status[0] === status
}

function focusStatus(status) {
  const same = isOnlyStatus(status) && !filters.overdue && !filters.mine
  filters.status = same ? [] : [status]
  filters.overdue = false
  filters.mine = false
  search()
}

function focusOverdue() {
  filters.overdue = !filters.overdue
  filters.status = []
  filters.mine = false
  search()
}

function focusMine() {
  filters.mine = !filters.mine
  filters.status = []
  filters.overdue = false
  search()
}

function go(pageNo) {
  page.value.pageNo = pageNo
  load()
}

function openDetail(row) {
  router.push({ name: 'task-detail', params: { id: row.id } })
}

function openCreate() {
  dialog.error = ''
  dialog.form = emptyForm()
  dialog.open = true
}

async function save() {
  if (dialog.saving) return
  dialog.error = ''
  const form = dialog.form
  if (!form.title.trim() || form.title.trim().length < 2) {
    dialog.error = '标题至少 2 字'
    return
  }
  if (!form.type) {
    dialog.error = '请选择工单类型'
    return
  }
  if (!form.deptId) {
    dialog.error = '请选择承办部门'
    return
  }
  if (!form.ownerId || !form.primaryAssigneeId) {
    dialog.error = '负责人与主责执行人都要选'
    return
  }
  if (!form.planEnd) {
    dialog.error = '请填写完成期限'
    return
  }
  if (form.ownerId === form.primaryAssigneeId) {
    dialog.error = '主责执行人不能是负责人本人'
    return
  }
  dialog.saving = true
  try {
    const actions = splitLines(form.actionList).map((content) => ({ content }))
    const created = await taskApi.create({
      title: form.title.trim(),
      type: form.type,
      deptId: form.deptId,
      ownerId: form.ownerId,
      primaryAssigneeId: form.primaryAssigneeId,
      priority: form.priority,
      planEnd: toApiDateTime(form.planEnd),
      description: form.description || null,
      acceptCriteria: splitLines(form.acceptCriteria),
      issueList: splitLines(form.issueList),
      actionList: actions
    })
    dialog.open = false
    await load()
    router.push({ name: 'task-detail', params: { id: created.taskId } })
  } catch (e) {
    dialog.error = e.message
  } finally {
    dialog.saving = false
  }
}

/** 筛选条件用 query 带过来，刷新与分享都能还原 */
onMounted(() => {
  const { status, overdue, deptId } = route.query
  if (typeof status === 'string' && status) filters.status = status.split(',').filter(Boolean)
  if (typeof deptId === 'string' && deptId) filters.deptId = deptId.split(',').map(Number).filter(Boolean)
  if (overdue === '1' || overdue === 'true') filters.overdue = true
  load()
})
</script>

<style scoped>
/* 导出控件并进筛选条，靠右对齐；换行时也贴着右边 */
.toolbar-export,
.create-row {
  margin-left: auto;
  max-width: 100%;
}

.create-row {
  display: flex;
  justify-content: flex-end;
}

.counts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.count-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: var(--surface);
  cursor: pointer;
  box-shadow: var(--shadow-card);
}

.count-chip:hover {
  background: #F7F8FA;
}

.count-chip.active {
  border-color: var(--txt);
  background: #F2F3F5;
}

.count-danger .count-value {
  color: var(--bad);
}

.count-label {
  color: var(--txt-2);
  font-size: 13px;
}

.count-value {
  font-size: 16px;
  font-weight: 600;
}

.count {
  color: var(--txt-3);
  font-size: 13px;
}

.row {
  cursor: pointer;
}

.title-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.title {
  font-weight: 500;
}

.sub-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  min-height: 0;
}

.muted {
  color: var(--txt-3);
  font-size: 12px;
}

.hint {
  margin: -4px 0 14px;
  color: var(--txt-3);
  font-size: 12px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
  color: var(--txt-2);
}
</style>
