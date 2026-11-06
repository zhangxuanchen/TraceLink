<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">人员管理</h2>
        <p class="page-sub">花名册由这里维护，工单的主责人、负责人只能从这里选；账号与角色也在这里改，四种角色决定菜单与可见数据</p>
      </div>
      <button class="btn btn-primary" @click="openCreate">新增人员</button>
    </div>

    <div class="toolbar">
      <input v-model="filters.keyword" placeholder="姓名或工号" style="width: 180px" @keyup.enter="search" />
      <select v-model="filters.deptId" @change="search">
        <option :value="null">全部部门</option>
        <option v-for="d in deptOptions" :key="d.id" :value="d.id">
          {{ '　'.repeat(d.level - 1) + d.name }}
        </option>
      </select>
      <select v-model="filters.status" @change="search">
        <option :value="null">全部状态</option>
        <option :value="1">在职</option>
        <option :value="3">试用</option>
        <option :value="2">离职</option>
      </select>
      <button class="btn" @click="search">查询</button>
      <button class="btn" @click="reset">重置</button>
      <span class="count">共 {{ page.total }} 人</span>
    </div>

    <div v-if="error" class="form-error">{{ error }}</div>

    <div class="card">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!page.records.length" class="empty">
        没有符合条件的人员，换个筛选条件或新增一位
      </div>
      <table v-else class="table">
        <thead>
          <tr>
            <th class="num">工号</th>
            <th>姓名</th>
            <th>部门</th>
            <th>职位</th>
            <th>直属上级</th>
            <th>入职日期</th>
            <th v-if="canSeeRoles" style="min-width: 268px">角色</th>
            <th>状态</th>
            <th style="width: 80px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in page.records" :key="row.id">
            <td class="num">{{ row.empNo }}</td>
            <td>
              <div>{{ row.name }}</div>
              <div class="muted small">{{ accountOf(row.id)?.username || '未建账号' }}</div>
            </td>
            <td>{{ row.deptName || '—' }}</td>
            <td>{{ row.positionName || '—' }}</td>
            <td>{{ row.managerName || '—' }}</td>
            <td class="num">{{ row.joinDate || '—' }}</td>
            <td v-if="canSeeRoles" class="role-cell">
              <template v-if="isAdmin">
                <label v-for="role in roles" :key="role.code" class="role-check"
                       :title="role.dataScopeLabel">
                  <input type="checkbox" :value="role.code"
                         :checked="roleDraft[row.id]?.includes(role.code)"
                         :disabled="savingRoleId === row.id || row.id === selfId"
                         @change="toggleRole(row.id, role.code, $event.target.checked)" />
                  <span>{{ role.name }}</span>
                </label>
                <button v-if="roleDirty(row.id)" class="btn-link role-save"
                        :disabled="savingRoleId === row.id" @click="saveRoles(row.id)">
                  {{ savingRoleId === row.id ? '保存中…' : '保存' }}
                </button>
                <span v-else-if="row.id === selfId" class="muted small">本人角色由另一位管理员改</span>
              </template>
              <template v-else>
                <span v-for="code in roleCodesOf(row.id)" :key="code" class="tag role-tag">{{ roleName(code) }}</span>
                <span v-if="!roleCodesOf(row.id).length" class="muted small">未分配</span>
              </template>
            </td>
            <td>
              <span class="tag" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span>
            </td>
            <td>
              <button class="btn-link" @click="openEdit(row)">编辑</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="roleError" class="form-error role-error">{{ roleError }}</div>
    </div>

    <div class="pager" v-if="page.total > page.pageSize">
      <button class="btn" :disabled="page.pageNo <= 1" @click="go(page.pageNo - 1)">上一页</button>
      <span class="num">{{ page.pageNo }} / {{ Math.ceil(page.total / page.pageSize) }}</span>
      <button class="btn" :disabled="page.pageNo * page.pageSize >= page.total" @click="go(page.pageNo + 1)">下一页</button>
    </div>

    <BaseModal v-if="dialog.open" :title="dialog.id ? '编辑人员' : '新增人员'" @close="dialog.open = false">
      <div v-if="dialog.error" class="form-error">{{ dialog.error }}</div>
      <div class="grid-2">
        <div class="field">
          <label>工号<span class="req">*</span></label>
          <input v-model="dialog.form.empNo" :disabled="dialog.saving" placeholder="如 A0004" />
        </div>
        <div class="field">
          <label>姓名<span class="req">*</span></label>
          <input v-model="dialog.form.name" :disabled="dialog.saving" placeholder="如 张三" />
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>所属部门<span class="req">*</span></label>
          <select v-model="dialog.form.deptId" :disabled="dialog.saving" @change="onDeptChange">
            <option :value="null" disabled>请选择</option>
            <option v-for="d in deptOptions" :key="d.id" :value="d.id">
              {{ '　'.repeat(d.level - 1) + d.name }}
            </option>
          </select>
        </div>
        <div class="field">
          <label>职位</label>
          <select v-model="dialog.form.positionId" :disabled="dialog.saving || !dialog.form.deptId">
            <option :value="null">未设置</option>
            <option v-for="p in positionOptions" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>直属上级</label>
          <select v-model="dialog.form.managerId" :disabled="dialog.saving">
            <option :value="null">未设置</option>
            <option v-for="e in managerOptions" :key="e.id" :value="e.id">{{ e.name }}（{{ e.empNo }}）</option>
          </select>
        </div>
        <div class="field">
          <label>人员状态</label>
          <select v-model.number="dialog.form.status" :disabled="dialog.saving">
            <option :value="1">在职</option>
            <option :value="3">试用</option>
            <option :value="2">离职</option>
          </select>
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>手机号</label>
          <input v-model="dialog.form.phone" :disabled="dialog.saving" />
        </div>
        <div class="field">
          <label>邮箱</label>
          <input v-model="dialog.form.email" :disabled="dialog.saving" />
        </div>
      </div>
      <div class="field">
        <label>入职日期</label>
        <input v-model="dialog.form.joinDate" type="date" :disabled="dialog.saving" />
      </div>
      <template #footer>
        <button class="btn" :disabled="dialog.saving" @click="dialog.open = false">取消</button>
        <button class="btn btn-primary" :disabled="dialog.saving" @click="save">
          {{ dialog.saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import BaseModal from '../components/BaseModal.vue'
import { deptApi, employeeApi, positionApi } from '../api/org.js'
import { settingsApi } from '../api/admin.js'
import { USER_KEY } from '../api/http.js'

const loading = ref(true)
const error = ref('')
const depts = ref([])
const employees = ref([])
const positions = ref([])
const page = ref({ total: 0, pageNo: 1, pageSize: 20, records: [] })

/** 角色与账号表：角色改在人员这一页，账号只读展示在姓名下方 */
const roles = ref([])
const accounts = ref([])
const roleDraft = reactive({})
const savingRoleId = ref(null)
const roleError = ref('')

const user = (() => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
  } catch {
    return {}
  }
})()
const isAdmin = computed(() => (user.roles || []).includes('SYS_ADMIN'))
const canSeeRoles = computed(() => (user.roles || []).some((r) => ['SYS_ADMIN', 'MANAGER'].includes(r)))
const selfId = user.employeeId || null

const filters = reactive({ keyword: '', deptId: null, status: null })
const dialog = reactive({ open: false, id: null, saving: false, error: '', form: emptyForm() })

function emptyForm() {
  return {
    empNo: '', name: '', deptId: null, positionId: null, managerId: null,
    phone: '', email: '', status: 1, joinDate: ''
  }
}

const deptOptions = computed(() => flatten(depts.value))
const positionOptions = computed(() => positions.value)
const managerOptions = computed(() => employees.value.filter((e) => e.id !== dialog.id))

function flatten(nodes, out = []) {
  for (const node of nodes) {
    out.push(node)
    if (node.children?.length) flatten(node.children, out)
  }
  return out
}

function statusLabel(status) {
  return { 1: '在职', 2: '离职', 3: '试用' }[status] || '未知'
}

function statusClass(status) {
  if (status === 1) return 'tag-ok'
  if (status === 3) return 'tag-warn'
  return 'tag-bad'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (!depts.value.length) {
      depts.value = await deptApi.tree()
      employees.value = await employeeApi.options()
    }
    if (canSeeRoles.value && !roles.value.length) {
      await loadAccounts()
    }
    const params = {
      keyword: filters.keyword || undefined,
      deptId: filters.deptId || undefined,
      status: filters.status ?? undefined,
      pageNo: page.value.pageNo,
      pageSize: page.value.pageSize
    }
    page.value = await employeeApi.page(params)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadAccounts() {
  const [roleList, accountList] = await Promise.all([settingsApi.roles(), settingsApi.accounts()])
  roles.value = roleList
  accounts.value = accountList
  for (const account of accountList) {
    roleDraft[account.employeeId] = [...(account.roleCodes || [])]
  }
}

function accountOf(employeeId) {
  return accounts.value.find((item) => item.employeeId === employeeId)
}

function roleName(code) {
  return roles.value.find((role) => role.code === code)?.name || code
}

function roleCodesOf(employeeId) {
  return accountOf(employeeId)?.roleCodes || []
}

function roleDirty(employeeId) {
  const current = roleCodesOf(employeeId)
  const draft = roleDraft[employeeId] || []
  return current.length !== draft.length || draft.some((code) => !current.includes(code))
}

function toggleRole(employeeId, code, checked) {
  const list = roleDraft[employeeId] || []
  roleDraft[employeeId] = checked ? [...list, code] : list.filter((item) => item !== code)
}

async function saveRoles(employeeId) {
  if (savingRoleId.value) return
  savingRoleId.value = employeeId
  roleError.value = ''
  try {
    accounts.value = await settingsApi.updateAccountRoles(employeeId, roleDraft[employeeId] || [])
    roleDraft[employeeId] = [...(accountOf(employeeId)?.roleCodes || [])]
  } catch (e) {
    roleError.value = e.message
    roleDraft[employeeId] = [...roleCodesOf(employeeId)]
  } finally {
    savingRoleId.value = null
  }
}

function search() {
  page.value.pageNo = 1
  load()
}

function reset() {
  filters.keyword = ''
  filters.deptId = null
  filters.status = null
  search()
}

function go(pageNo) {
  page.value.pageNo = pageNo
  load()
}

async function onDeptChange() {
  dialog.form.positionId = null
  positions.value = dialog.form.deptId ? await positionApi.list(dialog.form.deptId) : []
}

async function openCreate() {
  dialog.id = null
  dialog.error = ''
  dialog.form = emptyForm()
  positions.value = []
  dialog.open = true
  if (!employees.value.length) employees.value = await employeeApi.options()
}

async function openEdit(row) {
  dialog.id = row.id
  dialog.error = ''
  dialog.form = {
    empNo: row.empNo,
    name: row.name,
    deptId: row.deptId,
    positionId: row.positionId,
    managerId: row.managerId,
    phone: row.phone || '',
    email: row.email || '',
    status: row.status,
    joinDate: row.joinDate || ''
  }
  positions.value = row.deptId ? await positionApi.list(row.deptId) : []
  dialog.open = true
}

async function save() {
  if (dialog.saving) return
  dialog.error = ''
  if (!dialog.form.empNo.trim() || !dialog.form.name.trim()) {
    dialog.error = '工号与姓名都要填'
    return
  }
  if (!dialog.form.deptId) {
    dialog.error = '请选择所属部门'
    return
  }
  dialog.saving = true
  try {
    const payload = {
      ...dialog.form,
      joinDate: dialog.form.joinDate || null,
      phone: dialog.form.phone || null,
      email: dialog.form.email || null
    }
    if (dialog.id) {
      await employeeApi.update(dialog.id, payload)
    } else {
      await employeeApi.create(payload)
    }
    dialog.open = false
    employees.value = await employeeApi.options()
    await load()
  } catch (e) {
    dialog.error = e.message
  } finally {
    dialog.saving = false
  }
}

onMounted(load)
</script>

<style scoped>
.count {
  color: var(--txt-3);
  font-size: 13px;
}

.muted {
  color: var(--txt-3);
}

.small {
  font-size: 12px;
}

.role-cell {
  line-height: 2;
}

.role-check {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: 12px;
  color: var(--txt-2);
  font-size: 12px;
  white-space: nowrap;
}

.role-check input {
  height: auto;
  margin: 0;
}

.role-tag {
  margin-right: 6px;
}

.role-save {
  margin-left: 4px;
  font-size: 12px;
}

.role-error {
  margin: 12px 16px 0;
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
