<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">知识库</h2>
        <p class="page-sub">制度与历史问题措施统一入库；按数据范围检索，回答带可回溯的引用</p>
      </div>
      <div class="head-actions">
        <button v-if="isAdmin" class="btn" @click="openHistory">沉淀历史措施</button>
        <button v-if="isAdmin" class="btn btn-primary" @click="openUpload">上传文档</button>
        <button class="btn" :disabled="loadingDocs" @click="loadDocuments">{{ loadingDocs ? '加载中…' : '刷新' }}</button>
      </div>
    </div>

    <div v-if="degraded" class="notice">
      <span class="tag tag-warn">降级</span>
      当前未接入 RAGFlow，检索走本地全文（关键词）匹配，结果可能不完整；问答需要模型密钥，否则只能看引用原文。
    </div>
    <div v-if="loadError" class="form-error">{{ loadError }}</div>

    <div class="layout">
      <div class="main-col">
        <!-- 检索与问答 -->
        <div class="card block">
          <div class="block-head">
            <h3>检索与问答</h3>
            <span class="muted">范围只含你可见的制度与历史措施</span>
          </div>
          <div class="ask-bar">
            <textarea v-model="question" rows="2" placeholder="例如：冲压工位毛刺超标怎么处理？"
                      @keydown.ctrl.enter.prevent="submit" @keydown.meta.enter.prevent="submit"></textarea>
            <div class="ask-controls">
              <select v-model="domain" style="width: 150px">
                <option value="">全部知识域</option>
                <option v-for="d in KB_DOMAINS" :key="d.code" :value="d.code">{{ d.label }}</option>
              </select>
              <select v-model="deptId" style="width: 140px">
                <option :value="null">全部部门</option>
                <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ d.name }}</option>
              </select>
              <select v-model="topK" style="width: 108px">
                <option :value="5">引用 5 条</option>
                <option :value="6">引用 6 条</option>
                <option :value="8">引用 8 条</option>
              </select>
              <div class="seg">
                <button :class="{ on: mode === 'search' }" @click="mode = 'search'">检索</button>
                <button :class="{ on: mode === 'ask' }" @click="mode = 'ask'">智能问答</button>
              </div>
              <button class="btn btn-primary" :disabled="busy" @click="submit">
                {{ busy ? (mode === 'ask' ? '生成中…' : '检索中…') : (mode === 'ask' ? '提问' : '检索') }}
              </button>
              <button v-if="askState.running" class="btn" @click="stopAsk">停止</button>
            </div>
            <p class="muted small">Ctrl/⌘ + Enter 提交；问题 3 到 500 字。</p>
          </div>

          <div v-if="formError" class="form-error">{{ formError }}</div>

          <!-- 问答结果 -->
          <div v-if="mode === 'ask' && (askState.started || askState.running)" class="result">
            <div class="result-head">
              <span class="tag">{{ askState.running ? '生成中' : askState.done ? '已完成' : '已停止' }}</span>
              <span v-if="askState.retrieved" class="muted">
                召回 {{ askState.retrieved.total }} 条 · {{ retrieverLabel(askState.retrieved) }}
              </span>
            </div>
            <div v-if="askState.error" class="form-error">
              <b>{{ askState.error.code || '问答失败' }}</b>
              <span>{{ askState.error.message }}</span>
              <p v-if="askState.error.code === 'TL-5031'" class="muted small">
                模型密钥未配置（ARK_API_KEY），可先用「检索」直接查看命中的原文与引用。
              </p>
            </div>
            <div v-if="askState.answer" class="answer">{{ askState.answer }}</div>
            <div v-else-if="askState.running" class="empty">正在组织答案…</div>
            <div v-if="askState.citations.length" class="citations">
              <div class="citations-title">引用（点击可回溯原文）</div>
              <button v-for="(c, i) in askState.citations" :key="`${c.chunkId}-${i}`" class="citation"
                      @click="openChunk(c.chunkId)">
                <span class="citation-no num">[{{ i + 1 }}]</span>
                <span class="citation-title">{{ c.docTitle }}</span>
                <span class="muted small">第 {{ c.seq }} 片</span>
              </button>
            </div>
            <div v-if="askState.retrieved?.hits?.length" class="hits compact">
              <button v-for="hit in askState.retrieved.hits" :key="hit.chunkId" class="hit"
                      @click="openChunk(hit.chunkId)">
                <span class="hit-main">
                  <span class="hit-title">{{ hit.docTitle }}</span>
                  <span class="hit-excerpt">{{ hit.excerpt }}</span>
                </span>
                <span class="hit-meta num">{{ percent(hit.similarity) }}</span>
              </button>
            </div>
          </div>

          <!-- 纯检索结果 -->
          <div v-if="mode === 'search' && searchResult" class="result">
            <div class="result-head">
              <span class="tag" :class="searchResult.degraded ? 'tag-warn' : 'tag-ok'">
                {{ searchResult.degraded ? '本地降级' : 'RAGFlow 向量' }}
              </span>
              <span class="muted">命中 {{ searchResult.total }} 条 · 问题「{{ searchResult.question }}」</span>
            </div>
            <p v-if="searchResult.total === 0" class="empty">
              没有命中。可换一个说法，或确认该部门文档已入库完成。
            </p>
            <button v-for="hit in searchResult.hits" :key="hit.chunkId" class="hit"
                    @click="openChunk(hit.chunkId)">
              <span class="hit-main">
                <span class="hit-title">
                  {{ hit.docTitle }}
                  <span class="tag">{{ hit.domainLabel }}</span>
                  <span v-if="hit.deptName" class="tag">{{ hit.deptName }}</span>
                </span>
                <span class="hit-excerpt">{{ hit.excerpt }}</span>
              </span>
              <span class="hit-side">
                <span class="hit-meta num">{{ percent(hit.similarity) }}</span>
                <span class="muted small">第 {{ hit.seq }} 片</span>
              </span>
            </button>
          </div>
        </div>

        <!-- 文档库 -->
        <div class="card block">
          <div class="block-head">
            <h3>文档库</h3>
            <span class="muted">{{ documents.length }} 篇（最多显示 200 篇）</span>
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
            <input v-model="filterKeyword" placeholder="搜索标题或工单号" style="width: 200px" />
            <button class="btn" @click="loadDocuments">应用</button>
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
              <tr v-for="doc in documents" :key="doc.id">
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
        </div>
      </div>

      <aside class="side-col">
        <div class="card block">
          <div class="block-head"><h3>怎么用</h3></div>
          <ul class="tips">
            <li>上传制度、流程与历史措施，解析完成后才可检索。</li>
            <li>同一份文件重复上传按内容哈希复用，不会重复切片。</li>
            <li>历史问题与措施由已完成工单自动沉淀，按部门可见。</li>
            <li>引用可从切片回溯原文，重新解析后旧快照仍保留。</li>
          </ul>
        </div>
        <div class="card block">
          <div class="block-head"><h3>示例问题</h3></div>
          <button v-for="sample in SAMPLES" :key="sample" class="sample" @click="useSample(sample)">
            {{ sample }}
          </button>
        </div>
      </aside>
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

    <!-- 历史问题沉淀 -->
    <BaseModal v-if="historyOpen" title="沉淀历史问题与措施" @close="historyOpen = false">
      <p class="muted small history-tip">
        取最近一年已完成的工单，把「问题 + 措施 + 结果」整理成历史知识文档。内容重复会按哈希跳过。
      </p>
      <div class="grid-2">
        <div class="field">
          <label>限定部门</label>
          <select v-model="historyForm.deptId">
            <option :value="null">全部部门</option>
            <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>最多扫描</label>
          <select v-model="historyForm.limit">
            <option :value="50">50 篇</option>
            <option :value="100">100 篇</option>
            <option :value="200">200 篇</option>
          </select>
        </div>
      </div>
      <div v-if="historyResult" class="history-result">
        <span class="tag tag-ok">扫描 {{ historyResult.scanned }}</span>
        <span class="tag">新建 {{ historyResult.created }}</span>
        <span class="tag">跳过 {{ historyResult.skipped }}</span>
        <span class="muted small">可切到「历史问题与措施」知识域检索验证</span>
      </div>
      <div v-if="historyError" class="form-error">{{ historyError }}</div>
      <template #footer>
        <button class="btn" @click="historyOpen = false">关闭</button>
        <button class="btn btn-primary" :disabled="historyRunning" @click="runHistory">
          {{ historyRunning ? '沉淀中…' : '开始沉淀' }}
        </button>
      </template>
    </BaseModal>

    <!-- 切片原文 -->
    <BaseModal v-if="chunkOpen" :title="chunkView ? `${chunkView.docTitle} · 第 ${chunkView.seq} 片` : '切片原文'"
               @close="chunkOpen = false">
      <div v-if="chunkLoading" class="empty">加载中…</div>
      <div v-if="chunkError" class="form-error">{{ chunkError }}</div>
      <div v-if="chunkView" class="chunk-meta">
        <span class="tag">文档 #{{ chunkView.docId }}</span>
        <span v-if="chunkView.pageNo" class="tag">第 {{ chunkView.pageNo }} 页</span>
        <span v-if="chunkView.superseded" class="tag tag-warn">旧快照（原文已重建）</span>
        <span class="muted num small">{{ chunkView.chunkId }} · {{ fmtTime(chunkView.createdAt) }}</span>
      </div>
      <div v-if="chunkView" class="chunk-body">{{ chunkView.content }}</div>
      <template #footer>
        <button class="btn" @click="chunkOpen = false">关闭</button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { askStream, kbApi, KB_DOMAINS, KB_STATUS_CLASS, KB_VISIBILITY } from '../api/kb.js'
import { deptApi } from '../api/org.js'
import { USER_KEY } from '../api/http.js'
import BaseModal from '../components/BaseModal.vue'

const SAMPLES = [
  '冲压工位毛刺超标怎么处理？',
  '设备点检发现异常时的上报流程',
  '成品入库前需要哪些质检记录？'
]

const documents = ref([])
const deptOptions = ref([])
const loadingDocs = ref(false)
const loadError = ref('')
const degraded = ref(false)

const question = ref('')
const domain = ref('')
const deptId = ref(null)
const topK = ref(6)
const mode = ref('search')
const searching = ref(false)
const askRunning = ref(false)
const formError = ref('')
const searchResult = ref(null)

const askState = reactive({
  started: false,
  running: false,
  done: false,
  answer: '',
  citations: [],
  retrieved: null,
  error: null
})

const filterDomain = ref('')
const filterDept = ref(null)
const filterStatus = ref('')
const filterKeyword = ref('')

const uploadOpen = ref(false)
const uploading = ref(false)
const uploadError = ref('')
const uploadForm = reactive({ file: null, title: '', domain: 'POLICY', deptId: null, visibility: 'DEPT' })

const historyOpen = ref(false)
const historyRunning = ref(false)
const historyError = ref('')
const historyResult = ref(null)
const historyForm = reactive({ deptId: null, limit: 100 })

const chunkOpen = ref(false)
const chunkLoading = ref(false)
const chunkError = ref('')
const chunkView = ref(null)

let askController = null
let pollTimer = null

const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
  } catch {
    return {}
  }
})

const isAdmin = computed(() => (user.value.roles || []).includes('SYS_ADMIN'))
const busy = computed(() => searching.value || askRunning.value)

function retrieverLabel(result) {
  return result?.retriever === 'RAGFLOW' ? 'RAGFlow 向量检索' : '本地全文降级'
}

function percent(value) {
  return Number(value || 0).toFixed(2)
}

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
  } catch (e) {
    loadError.value = e.message
  } finally {
    loadingDocs.value = false
  }
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

function validateQuestion() {
  const text = question.value.trim()
  if (text.length < 3) {
    formError.value = '问题至少 3 个字'
    return null
  }
  if (text.length > 500) {
    formError.value = '问题不超过 500 字'
    return null
  }
  formError.value = ''
  return text
}

function submit() {
  if (mode.value === 'ask') {
    runAsk()
  } else {
    runSearch()
  }
}

async function runSearch() {
  const text = validateQuestion()
  if (!text) return
  stopAsk()
  searching.value = true
  searchResult.value = null
  askState.started = false
  try {
    const result = await kbApi.search({
      question: text,
      domain: domain.value || null,
      deptId: deptId.value || null,
      topK: topK.value
    })
    searchResult.value = result
    degraded.value = Boolean(result.degraded)
  } catch (e) {
    formError.value = e.message
  } finally {
    searching.value = false
  }
}

async function runAsk() {
  const text = validateQuestion()
  if (!text) return
  stopAsk()
  Object.assign(askState, {
    started: true,
    running: true,
    done: false,
    answer: '',
    citations: [],
    retrieved: null,
    error: null
  })
  searchResult.value = null
  askRunning.value = true
  askController = new AbortController()
  try {
    await askStream({
      question: text,
      domain: domain.value || null,
      deptId: deptId.value || null,
      topK: topK.value
    }, {
      retrieved: (result) => {
        askState.retrieved = result
        degraded.value = Boolean(result?.degraded)
      },
      delta: (chunk) => {
        askState.answer += chunk
      },
      citations: (list) => {
        askState.citations = list || []
      },
      done: () => {
        askState.running = false
        askState.done = true
      },
      error: (payload) => {
        askState.running = false
        askState.error = payload
      }
    }, askController.signal)
  } catch (e) {
    if (e.name !== 'AbortError') {
      askState.error = { code: e.code || '', message: e.message }
    }
  } finally {
    askState.running = false
    askRunning.value = false
  }
}

function stopAsk() {
  if (askController) {
    askController.abort()
    askController = null
  }
  askState.running = false
}

function useSample(sample) {
  question.value = sample
  mode.value = 'ask'
  runAsk()
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

function openHistory() {
  historyForm.deptId = filterDept.value || null
  historyForm.limit = 100
  historyResult.value = null
  historyError.value = ''
  historyOpen.value = true
}

async function runHistory() {
  historyRunning.value = true
  historyError.value = ''
  historyResult.value = null
  try {
    historyResult.value = await kbApi.history(historyForm.deptId, historyForm.limit)
    await loadDocuments()
    startPolling()
  } catch (e) {
    historyError.value = e.message
  } finally {
    historyRunning.value = false
  }
}

async function openChunk(chunkId) {
  chunkOpen.value = true
  chunkLoading.value = true
  chunkError.value = ''
  chunkView.value = null
  try {
    chunkView.value = await kbApi.chunk(chunkId)
  } catch (e) {
    chunkError.value = e.message
  } finally {
    chunkLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadDepts(), loadDocuments()])
  if (documents.value.some((d) => d.syncStatus === 'PARSING' || d.syncStatus === 'PENDING')) {
    startPolling()
  }
})

onBeforeUnmount(() => {
  stopAsk()
  stopPolling()
})
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.notice {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px;
  margin-bottom: 12px;
  border: 1px solid rgba(178, 106, 0, 0.28);
  border-radius: var(--radius-sm);
  background: rgba(178, 106, 0, 0.05);
  color: var(--txt-2);
  font-size: 13px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 270px;
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

.ask-bar textarea {
  width: 100%;
  resize: vertical;
  min-height: 62px;
}

.ask-controls {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 8px;
}

.seg {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.seg button {
  height: 32px;
  padding: 0 12px;
  border: none;
  background: var(--surface);
  color: var(--txt-2);
  cursor: pointer;
  font-size: 13px;
}

.seg button + button {
  border-left: 1px solid var(--divider);
}

.seg button.on {
  background: var(--txt);
  color: #fff;
}

.result {
  margin-top: 12px;
  border-top: 1px solid var(--divider);
  padding-top: 10px;
}

.result-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.answer {
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.7;
  padding: 10px 12px;
  background: #FBFBFC;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
}

.citations {
  margin-top: 10px;
}

.citations-title {
  font-size: 12px;
  color: var(--txt-2);
  margin-bottom: 6px;
}

.citation {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  margin: 0 6px 6px 0;
  padding: 3px 10px;
  border: 1px solid var(--divider);
  border-radius: 12px;
  background: var(--surface);
  cursor: pointer;
  font-size: 12px;
}

.citation:hover {
  border-color: var(--info);
}

.citation-no {
  color: var(--info);
}

.citation-title {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hits {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
}

.hit {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 10px 8px;
  border: none;
  border-top: 1px solid var(--divider);
  background: none;
  text-align: left;
  cursor: pointer;
}

.hits.compact .hit:first-child {
  border-top: none;
}

.hit:hover {
  background: #F8F9FA;
}

.hit-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.hit-title {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 13px;
  font-weight: 500;
}

.hit-excerpt {
  color: var(--txt-2);
  font-size: 12px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hit-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex: none;
}

.hit-meta {
  font-size: 12px;
  color: var(--txt-2);
}

.doc-title {
  font-size: 13px;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tips {
  margin: 0;
  padding-left: 18px;
  color: var(--txt-2);
  font-size: 12px;
  line-height: 1.8;
}

.sample {
  display: block;
  width: 100%;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--txt-2);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.sample:hover {
  border-color: var(--info);
  color: var(--txt);
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

.modal-error {
  margin: 0 auto 0 0;
}

.history-tip {
  margin: 0 0 12px;
  line-height: 1.7;
}

.history-result {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  padding: 10px 12px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  margin-bottom: 8px;
}

.chunk-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.chunk-body {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.8;
  padding: 12px;
  background: #FBFBFC;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  max-height: 46vh;
  overflow: auto;
}

@media (max-width: 1024px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .doc-title {
    max-width: none;
    white-space: normal;
  }
}
</style>
