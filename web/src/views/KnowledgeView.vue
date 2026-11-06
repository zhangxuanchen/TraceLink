<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">知识库</h2>
        <p class="page-sub">制度与历史问题措施统一入库，按数据范围可见</p>
      </div>
    </div>

    <div v-if="loadError" class="form-error">{{ loadError }}</div>

    <!-- 文档库 -->
    <div class="card block">
      <div class="block-head">
        <h3>文档库</h3>
        <span class="muted">共 {{ documents.length }} 篇</span>
      </div>
      <div class="toolbar">
            <select v-model="filterDomain" style="width: 150px">
              <option value="">全部知识域</option>
              <option v-for="d in KB_DOMAINS" :key="d.code" :value="d.code">{{ d.label }}</option>
            </select>
            <select v-model="filterDept" style="width: 140px">
              <option :value="null">全部部门</option>
              <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ d.name }}</option>
            </select>
            <select v-model="filterStatus" style="width: 120px">
              <option value="">全部状态</option>
              <option value="PENDING">待解析</option>
              <option value="PARSING">解析中</option>
              <option value="READY">就绪</option>
              <option value="FAILED">失败</option>
            </select>
            <input v-model="filterKeyword" placeholder="搜索标题或内容" style="width: 220px" @keyup.enter="search" />
            <button class="btn" :disabled="loadingDocs" @click="search">搜索</button>
            <button v-if="isAdmin" class="btn btn-primary" @click="openUpload">上传文档</button>
          </div>

          <table class="table">
            <thead>
              <tr>
                <th>标题</th>
                <th style="width: 112px">知识域</th>
                <th style="width: 100px">部门</th>
                <th style="width: 92px">可见范围</th>
                <th style="width: 68px" class="num">切片</th>
                <th style="width: 88px">状态</th>
                <th style="width: 148px">同步时间</th>
                <th v-if="isAdmin" style="width: 88px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="doc in pagedDocuments" :key="doc.id">
                <td>
                  <div class="doc-title">{{ doc.title }}</div>
                  <div v-if="doc.sourceRef || doc.syncMsg" class="muted small">
                    <span v-if="doc.sourceRef" class="num">{{ doc.sourceRef }}</span>
                    <span v-if="doc.syncMsg" :title="doc.syncMsg"> · {{ doc.syncMsg }}</span>
                  </div>
                </td>
                <td><span class="tag">{{ doc.domainLabel }}</span></td>
                <td>{{ doc.deptName || '—' }}</td>
                <td class="small">{{ doc.visibilityLabel }}</td>
                <td class="num">{{ doc.chunkCnt ?? '—' }}</td>
                <td><span class="tag" :class="KB_STATUS_CLASS[doc.syncStatus] || ''">{{ doc.syncStatusLabel }}</span></td>
                <td class="num small">{{ fmtTime(doc.syncedAt || doc.createdAt) }}</td>
                <td v-if="isAdmin">
                  <button v-if="doc.syncStatus !== 'PARSING' || doc.sourceType !== 'UPLOAD'"
                          class="btn-link" @click="reparse(doc)">重新解析</button>
                  <span v-else class="muted small">解析中</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="!documents.length" class="empty">还没有可见的文档</div>
          <div v-else class="pager">
            <span class="muted small">
              第 {{ pageStart }}–{{ pageEnd }} 条，共 {{ documents.length }} 条
            </span>
            <div class="pager-btns">
              <button class="btn" :disabled="page <= 1" @click="page -= 1">上一页</button>
              <span class="muted small">{{ page }} / {{ totalPages }}</span>
              <button class="btn" :disabled="page >= totalPages" @click="page += 1">下一页</button>
            </div>
          </div>
    </div>

    <!-- 上传文档 -->
    <BaseModal v-if="uploadOpen" title="上传知识文档" @close="closeUpload">
      <div class="field">
        <label>文件<span class="req">*</span></label>
        <label class="btn file-btn">
          {{ uploadForm.file ? uploadForm.file.name : '选择文件' }}
          <input type="file" accept=".pdf,.docx,.md,.markdown,.txt" hidden @change="onPickFile" />
        </label>
        <span class="muted small">支持 PDF、DOCX、Markdown、TXT，单文件不超过 50 MB</span>
      </div>
      <div class="field">
        <label>标题</label>
        <input v-model="uploadForm.title" placeholder="默认取文件名，不超过 200 字" />
      </div>
      <div class="grid-2">
        <div class="field">
          <label>知识域</label>
          <select v-model="uploadForm.domain">
            <option v-for="d in KB_DOMAINS" :key="d.code" :value="d.code">{{ d.label }}</option>
          </select>
        </div>
        <div class="field">
          <label>所属部门</label>
          <select v-model="uploadForm.deptId">
            <option :value="null">不限（全员可见）</option>
            <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select>
        </div>
      </div>
      <div v-if="uploadForm.deptId" class="field">
        <label>可见范围</label>
        <select v-model="uploadForm.visibility">
          <option v-for="v in KB_VISIBILITY" :key="v.code" :value="v.code">{{ v.label }}</option>
        </select>
      </div>
      <p class="muted small">上传后进入待解析，本地解析约 1 分钟，RAGFlow 远端解析以同步状态为准。</p>
      <template #footer>
        <div v-if="uploadError" class="form-error modal-error">{{ uploadError }}</div>
        <button class="btn" @click="closeUpload">取消</button>
        <button class="btn btn-primary" :disabled="uploading" @click="submitUpload">
          {{ uploading ? '上传中…' : '上传并解析' }}
        </button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { kbApi, KB_DOMAINS, KB_STATUS_CLASS, KB_VISIBILITY } from '../api/kb.js'
import { deptApi } from '../api/org.js'
import { USER_KEY } from '../api/http.js'
import BaseModal from '../components/BaseModal.vue'

const documents = ref([])
const deptOptions = ref([])
const loadingDocs = ref(false)
const loadError = ref('')

const filterDomain = ref('')
const filterDept = ref(null)
const filterStatus = ref('')
const filterKeyword = ref('')

const page = ref(1)
const pageSize = 20
const totalPages = computed(() => Math.max(1, Math.ceil(documents.value.length / pageSize)))
const pageStart = computed(() => (documents.value.length ? (page.value - 1) * pageSize + 1 : 0))
const pageEnd = computed(() => Math.min(page.value * pageSize, documents.value.length))
const pagedDocuments = computed(() => {
  const start = (page.value - 1) * pageSize
  return documents.value.slice(start, start + pageSize)
})

const uploadOpen = ref(false)
const uploading = ref(false)
const uploadError = ref('')
const uploadForm = reactive({ file: null, title: '', domain: 'POLICY', deptId: null, visibility: 'DEPT' })

let pollTimer = null

const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
  } catch {
    return {}
  }
})

const isAdmin = computed(() => (user.value.roles || []).includes('SYS_ADMIN'))

function fmtTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function flatten(nodes, level = 1) {
  const list = []
  for (const node of nodes || []) {
    list.push({ id: node.id, name: `${'　'.repeat(level - 1)}${node.name}` })
    list.push(...flatten(node.children, level + 1))
  }
  return list
}

async function loadDepts() {
  try {
    deptOptions.value = flatten(await deptApi.tree())
  } catch {
    deptOptions.value = []
  }
}

async function loadDocuments() {
  loadingDocs.value = true
  loadError.value = ''
  try {
    documents.value = await kbApi.documents({
      domain: filterDomain.value || undefined,
      deptId: filterDept.value || undefined,
      status: filterStatus.value || undefined,
      keyword: filterKeyword.value.trim() || undefined
    })
    if (page.value > totalPages.value) page.value = totalPages.value
  } catch (e) {
    loadError.value = e.message
  } finally {
    loadingDocs.value = false
  }
}

/** 搜索与筛选从第一页重新看，轮询刷新时保留当前页 */
function search() {
  page.value = 1
  loadDocuments()
}

/** 已入库文档若有仍在解析的，轮询同步状态直到就绪或失败 */
function startPolling() {
  stopPolling()
  let rounds = 0
  const tick = async () => {
    rounds += 1
    await loadDocuments()
    const pending = documents.value.filter((d) => d.syncStatus === 'PARSING' || d.syncStatus === 'PENDING')
    if (!pending.length || rounds > 30) {
      pollTimer = null
      return
    }
    if (isAdmin.value) {
      for (const doc of pending) {
        try {
          await kbApi.sync(doc.id)
        } catch {
          // 远端暂不可达时保留当前状态，下一轮继续
        }
      }
    }
    pollTimer = setTimeout(tick, 1500)
  }
  pollTimer = setTimeout(tick, 1200)
}

function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function openUpload() {
  uploadForm.file = null
  uploadForm.title = ''
  uploadForm.domain = 'POLICY'
  uploadForm.deptId = filterDept.value || null
  uploadForm.visibility = 'DEPT'
  uploadError.value = ''
  uploadOpen.value = true
}

function closeUpload() {
  if (uploading.value) return
  uploadOpen.value = false
}

function onPickFile(event) {
  uploadForm.file = event.target.files?.[0] || null
  event.target.value = ''
}

async function submitUpload() {
  if (!uploadForm.file) {
    uploadError.value = '请选择要上传的文件'
    return
  }
  uploading.value = true
  uploadError.value = ''
  try {
    await kbApi.upload(uploadForm.file, {
      title: uploadForm.title.trim() || null,
      domain: uploadForm.domain,
      deptId: uploadForm.deptId,
      visibility: uploadForm.deptId ? uploadForm.visibility : null
    })
    uploadOpen.value = false
    filterDomain.value = ''
    filterStatus.value = ''
    await loadDocuments()
    startPolling()
  } catch (e) {
    uploadError.value = e.message
  } finally {
    uploading.value = false
  }
}

async function reparse(doc) {
  loadError.value = ''
  try {
    await kbApi.reparse(doc.id)
    await loadDocuments()
    startPolling()
  } catch (e) {
    loadError.value = e.message
  }
}

/** 知识域、部门、状态改动即生效；关键字按「搜索」或回车提交，标题与切片内容一起匹配 */
watch([filterDomain, filterDept, filterStatus], () => search())

onMounted(async () => {
  await Promise.all([loadDepts(), loadDocuments()])
  if (documents.value.some((d) => d.syncStatus === 'PARSING' || d.syncStatus === 'PENDING')) {
    startPolling()
  }
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
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

.doc-title {
  font-size: 13px;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  margin-top: 4px;
}

.pager-btns {
  display: flex;
  align-items: center;
  gap: 10px;
}

.modal-error {
  margin: 0 auto 0 0;
}

@media (max-width: 1024px) {
  .doc-title {
    max-width: none;
    white-space: normal;
  }
}
</style>
