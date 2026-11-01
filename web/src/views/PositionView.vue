<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">职位管理</h2>
        <p class="page-sub">职位归属部门，管理岗可被选为工单负责人</p>
      </div>
      <button class="btn btn-primary" @click="openCreate">新建职位</button>
    </div>

    <div class="toolbar">
      <select v-model="filterDeptId" @change="load">
        <option :value="null">全部部门</option>
        <option v-for="d in deptOptions" :key="d.id" :value="d.id">
          {{ '　'.repeat(d.level - 1) + d.name }}
        </option>
      </select>
      <span class="count">共 {{ list.length }} 个职位</span>
    </div>

    <div v-if="error" class="form-error">{{ error }}</div>

    <div class="card">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!list.length" class="empty">
        {{ filterDeptId ? '该部门还没有职位，点右上角新建' : '还没有职位，先建一个' }}
      </div>
      <table v-else class="table">
        <thead>
          <tr>
            <th>职位编码</th>
            <th>职位名称</th>
            <th>所属部门</th>
            <th class="num">职级</th>
            <th>管理岗</th>
            <th>状态</th>
            <th style="width: 120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td class="num">{{ row.code }}</td>
            <td>{{ row.name }}</td>
            <td>{{ deptName(row.deptId) }}</td>
            <td class="num">{{ row.jobLevel }}</td>
            <td>
              <span class="tag" :class="row.isManager === 1 ? 'tag-warn' : ''">
                {{ row.isManager === 1 ? '是' : '否' }}
              </span>
            </td>
            <td>
              <span class="tag" :class="row.status === 1 ? 'tag-ok' : ''">
                {{ row.status === 1 ? '启用' : '停用' }}
              </span>
            </td>
            <td class="ops">
              <button class="btn-link" @click="openEdit(row)">编辑</button>
              <button class="btn-link btn-danger" @click="remove(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <BaseModal v-if="dialog.open" :title="dialog.id ? '编辑职位' : '新建职位'" @close="dialog.open = false">
      <div v-if="dialog.error" class="form-error">{{ dialog.error }}</div>
      <div class="grid-2">
        <div class="field">
          <label>所属部门<span class="req">*</span></label>
          <select v-model="dialog.form.deptId" :disabled="dialog.saving">
            <option :value="null" disabled>请选择</option>
            <option v-for="d in deptOptions" :key="d.id" :value="d.id">
              {{ '　'.repeat(d.level - 1) + d.name }}
            </option>
          </select>
        </div>
        <div class="field">
          <label>职位编码<span class="req">*</span></label>
          <input v-model="dialog.form.code" placeholder="如 PROD01" :disabled="dialog.saving" />
        </div>
      </div>
      <div class="field">
        <label>职位名称<span class="req">*</span></label>
        <input v-model="dialog.form.name" placeholder="如 生产经理" :disabled="dialog.saving" />
      </div>
      <div class="grid-2">
        <div class="field">
          <label>职级</label>
          <input v-model.number="dialog.form.jobLevel" type="number" min="1" max="9" :disabled="dialog.saving" />
        </div>
        <div class="field">
          <label>管理岗</label>
          <select v-model.number="dialog.form.isManager" :disabled="dialog.saving">
            <option :value="0">否</option>
            <option :value="1">是</option>
          </select>
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>排序号</label>
          <input v-model.number="dialog.form.sortNo" type="number" :disabled="dialog.saving" />
        </div>
        <div class="field">
          <label>状态</label>
          <select v-model.number="dialog.form.status" :disabled="dialog.saving">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </div>
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
import { deptApi, positionApi } from '../api/org.js'

const loading = ref(true)
const error = ref('')
const list = ref([])
const depts = ref([])
const filterDeptId = ref(null)

const dialog = reactive({ open: false, id: null, saving: false, error: '', form: emptyForm() })

function emptyForm() {
  return { deptId: null, code: '', name: '', jobLevel: 1, isManager: 0, sortNo: 0, status: 1 }
}

const deptOptions = computed(() => flatten(depts.value))
const deptNames = computed(() => {
  const map = {}
  flatten(depts.value).forEach((d) => { map[d.id] = d.name })
  return map
})

function flatten(nodes, out = []) {
  for (const node of nodes) {
    out.push(node)
    if (node.children?.length) flatten(node.children, out)
  }
  return out
}

function deptName(id) {
  return deptNames.value[id] || '—'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (!depts.value.length) depts.value = await deptApi.tree()
    list.value = await positionApi.list(filterDeptId.value)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialog.id = null
  dialog.error = ''
  dialog.form = { ...emptyForm(), deptId: filterDeptId.value || null }
  dialog.open = true
}

function openEdit(row) {
  dialog.id = row.id
  dialog.error = ''
  dialog.form = {
    deptId: row.deptId,
    code: row.code,
    name: row.name,
    jobLevel: row.jobLevel,
    isManager: row.isManager,
    sortNo: row.sortNo,
    status: row.status
  }
  dialog.open = true
}

async function save() {
  if (dialog.saving) return
  dialog.error = ''
  if (!dialog.form.deptId) {
    dialog.error = '请选择所属部门'
    return
  }
  if (!dialog.form.code.trim() || !dialog.form.name.trim()) {
    dialog.error = '职位编码与名称都要填'
    return
  }
  dialog.saving = true
  try {
    if (dialog.id) {
      await positionApi.update(dialog.id, dialog.form)
    } else {
      await positionApi.create(dialog.form)
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
  const ok = window.confirm(`删除职位「${row.name}」？该职位下还有人员时会被拒绝。`)
  if (!ok) return
  error.value = ''
  try {
    await positionApi.remove(row.id)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<style scoped>
.count {
  color: var(--txt-3);
  font-size: 13px;
}

.ops {
  display: flex;
  gap: 12px;
}

.btn-danger {
  color: var(--bad);
}
</style>
