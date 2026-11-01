<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">部门管理</h2>
        <p class="page-sub">维护组织层级与部门负责人，路径与层级由系统自动计算</p>
      </div>
      <button class="btn btn-primary" @click="openCreate(null)">新建部门</button>
    </div>

    <div v-if="error" class="form-error">{{ error }}</div>

    <div class="card">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!rows.length" class="empty">
        还没有部门，先建一个根部门，再往下加下级部门
      </div>
      <table v-else class="table">
        <thead>
          <tr>
            <th style="width: 34%">部门</th>
            <th>编码</th>
            <th>负责人</th>
            <th class="num">编制</th>
            <th>层级</th>
            <th>状态</th>
            <th style="width: 160px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>
              <span class="indent" :style="{ paddingLeft: (row.level - 1) * 18 + 'px' }">
                <span v-if="row.level > 1" class="tree-line">└</span>
                {{ row.name }}
              </span>
            </td>
            <td class="num">{{ row.code }}</td>
            <td>{{ row.leaderName || '未设置' }}</td>
            <td class="num">{{ row.headcount ?? '—' }}</td>
            <td class="num">L{{ row.level }}</td>
            <td>
              <span class="tag" :class="row.status === 1 ? 'tag-ok' : ''">
                {{ row.status === 1 ? '启用' : '停用' }}
              </span>
            </td>
            <td class="ops">
              <button class="btn-link" @click="openCreate(row)">加下级</button>
              <button class="btn-link" @click="openEdit(row)">编辑</button>
              <button class="btn-link btn-danger" @click="remove(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <BaseModal v-if="dialog.open" :title="dialog.id ? '编辑部门' : '新建部门'" @close="closeDialog">
      <div v-if="dialog.error" class="form-error">{{ dialog.error }}</div>
      <div class="grid-2">
        <div class="field">
          <label>部门编码<span class="req">*</span></label>
          <input v-model="dialog.form.code" placeholder="如 PROD" :disabled="dialog.saving" />
        </div>
        <div class="field">
          <label>部门名称<span class="req">*</span></label>
          <input v-model="dialog.form.name" placeholder="如 生产部" :disabled="dialog.saving" />
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>上级部门</label>
          <select v-model="dialog.form.parentId" :disabled="dialog.saving">
            <option :value="0">（作为根部门）</option>
            <option v-for="d in parentOptions" :key="d.id" :value="d.id">
              {{ '　'.repeat(d.level - 1) + d.name }}
            </option>
          </select>
        </div>
        <div class="field">
          <label>部门负责人</label>
          <select v-model="dialog.form.leaderEmployeeId" :disabled="dialog.saving">
            <option :value="null">未设置</option>
            <option v-for="e in employees" :key="e.id" :value="e.id">{{ e.name }}（{{ e.empNo }}）</option>
          </select>
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>编制人数</label>
          <input v-model.number="dialog.form.headcount" type="number" min="0" :disabled="dialog.saving" />
        </div>
        <div class="field">
          <label>排序号</label>
          <input v-model.number="dialog.form.sortNo" type="number" :disabled="dialog.saving" />
        </div>
      </div>
      <div class="field">
        <label>状态</label>
        <select v-model.number="dialog.form.status" :disabled="dialog.saving">
          <option :value="1">启用</option>
          <option :value="0">停用</option>
        </select>
      </div>
      <template #footer>
        <button class="btn" :disabled="dialog.saving" @click="closeDialog">取消</button>
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
import { deptApi, employeeApi } from '../api/org.js'

const loading = ref(true)
const error = ref('')
const tree = ref([])
const employees = ref([])

const rows = computed(() => flatten(tree.value))
const parentOptions = computed(() => flatten(tree.value).filter((d) => d.id !== dialog.id))

const dialog = reactive({
  open: false,
  id: null,
  saving: false,
  error: '',
  form: emptyForm()
})

function emptyForm() {
  return { code: '', name: '', parentId: 0, leaderEmployeeId: null, headcount: null, sortNo: 0, status: 1 }
}

function flatten(nodes, out = []) {
  for (const node of nodes) {
    out.push({ ...node, children: undefined })
    if (node.children?.length) flatten(node.children, out)
  }
  return out
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [treeData, employeeData] = await Promise.all([deptApi.tree(), employeeApi.options()])
    employees.value = employeeData || []
    const nameMap = {}
    employees.value.forEach((e) => { nameMap[e.id] = e.name })
    tree.value = withLeaderName(treeData || [], nameMap)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function withLeaderName(nodes, nameMap) {
  return nodes.map((n) => ({
    ...n,
    leaderName: nameMap[n.leaderEmployeeId] || null,
    children: n.children?.length ? withLeaderName(n.children, nameMap) : []
  }))
}

function openCreate(parent) {
  dialog.id = null
  dialog.error = ''
  dialog.form = { ...emptyForm(), parentId: parent ? parent.id : 0 }
  dialog.open = true
}

function openEdit(row) {
  dialog.id = row.id
  dialog.error = ''
  dialog.form = {
    code: row.code,
    name: row.name,
    parentId: row.parentId ?? 0,
    leaderEmployeeId: row.leaderEmployeeId ?? null,
    headcount: row.headcount ?? null,
    sortNo: row.sortNo ?? 0,
    status: row.status ?? 1
  }
  dialog.open = true
}

function closeDialog() {
  dialog.open = false
}

async function save() {
  if (dialog.saving) return
  dialog.error = ''
  if (!dialog.form.code.trim() || !dialog.form.name.trim()) {
    dialog.error = '部门编码与名称都要填'
    return
  }
  dialog.saving = true
  try {
    const payload = { ...dialog.form, parentId: dialog.form.parentId || 0 }
    if (dialog.id) {
      await deptApi.update(dialog.id, payload)
    } else {
      await deptApi.create(payload)
    }
    dialog.open = false
    await load()
  } catch (e) {
    dialog.error = e.message
  } finally {
    dialog.saving = false
  }
}

async function remove(row) {
  const ok = window.confirm(`删除部门「${row.name}」？有下级部门或人员时会被拒绝。`)
  if (!ok) return
  error.value = ''
  try {
    await deptApi.remove(row.id)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<style scoped>
.indent {
  color: var(--txt);
}

.tree-line {
  color: var(--txt-3);
  margin-right: 4px;
}

.ops {
  display: flex;
  gap: 12px;
  white-space: nowrap;
}
</style>
