<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">会话助手</h2>
        <p class="page-sub">只读查看工单、统计、人员与知识；回答带引用与工具调用记录，不代填建单字段</p>
      </div>
      <div class="head-actions">
        <button class="btn" :disabled="creating" @click="createSession">{{ creating ? '创建中…' : '新建会话' }}</button>
        <button class="btn" :disabled="loadingSessions" @click="loadSessions">刷新</button>
      </div>
    </div>

    <div v-if="loadError" class="form-error">{{ loadError }}</div>
    <div v-if="notice" class="notice">
      <span class="tag tag-warn">提示</span>
      <span>{{ notice }}</span>
      <button class="btn-link" @click="notice = ''">关闭</button>
    </div>

    <div class="layout">
      <aside class="card side">
        <div class="side-head">
          <h3>我的会话</h3>
          <span class="muted">{{ sessions.length }}</span>
        </div>
        <div v-if="!sessions.length" class="empty side-empty">还没有会话，点右上角「新建会话」开始</div>
        <div v-for="s in sessions" :key="s.id" class="session" :class="{ active: s.id === activeId }"
             @click="selectSession(s)">
          <div class="session-main">
            <div class="session-title">{{ s.title }}</div>
            <div class="session-meta">
              <span class="num">{{ fmtTime(s.lastMsgAt || s.createdAt) }}</span>
              <span>{{ s.msgCount || 0 }} 条</span>
              <span class="tag" :class="s.status === 'ARCHIVED' ? 'tag-warn' : ''">{{ s.statusLabel }}</span>
            </div>
          </div>
          <button v-if="s.status !== 'ARCHIVED'" class="btn-link session-op"
                  @click.stop="archiveSession(s)">归档</button>
        </div>
      </aside>

      <div class="card chat">
        <div v-if="!activeSession" class="empty chat-empty">
          <p class="empty-title">选一个会话，或直接新建</p>
          <p class="muted">可以问工单进度、部门指标、人员负载与制度流程；也可以先试试下面的问题。</p>
          <div class="samples">
            <button v-for="sample in CHAT_SAMPLES" :key="sample" class="sample" @click="askSample(sample)">
              {{ sample }}
            </button>
          </div>
        </div>

        <template v-else>
          <div class="chat-head">
            <div>
              <div class="chat-title">{{ activeSession.title }}</div>
              <div class="muted small">
                {{ activeSession.scopeTypeLabel }} · {{ activeSession.statusLabel }} · {{ activeSession.msgCount || 0 }} 条消息
              </div>
            </div>
            <button v-if="activeSession.status !== 'ARCHIVED'" class="btn"
                    @click="archiveSession(activeSession)">归档会话</button>
          </div>

          <div ref="scrollEl" class="messages">
            <div v-if="loadingMessages" class="empty">加载会话记录…</div>
            <div v-else-if="!messages.length && !live.active" class="empty">
              还没有提问。可以问「生产部这周还有几张单没验收？」
            </div>

            <div v-for="m in messages" :key="m.id" class="msg" :class="m.role === 'USER' ? 'msg-user' : 'msg-assistant'">
              <template v-if="m.role === 'USER'">
                <div class="bubble user-bubble">{{ m.content }}</div>
                <div class="msg-meta num">{{ fmtTime(m.createdAt) }}</div>
              </template>
              <template v-else>
                <div class="bubble assistant-bubble">
                  <div class="bubble-head">
                    <span class="tag" :class="modelClass(m)">{{ modelLabel(m) }}</span>
                    <span class="muted small">
                      {{ fmtTime(m.createdAt) }}
                      <span v-if="m.latencyMs"> · {{ m.latencyMs }} ms</span>
                      <span v-if="m.promptVer"> · {{ m.promptVer }}</span>
                    </span>
                  </div>
                  <div class="answer" v-html="renderText(m.content)"></div>
                  <div v-if="toolsOf(m).length" class="tools">
                    <span v-for="(tool, i) in toolsOf(m)" :key="`${m.id}-${i}`" class="tool"
                          :title="argsText(tool.args)">
                      <span class="tool-name">{{ tool.label || tool.name }}</span>
                      <span class="num">{{ tool.resultCount ?? 0 }} 条</span>
                      <span v-if="tool.elapsedMs" class="muted num">{{ tool.elapsedMs }} ms</span>
                    </span>
                  </div>
                  <div v-if="citationsOf(m).length" class="citations">
                    <span class="citations-title">引用</span>
                    <button v-for="(c, i) in citationsOf(m)" :key="`${m.id}-c-${i}`" class="citation"
                            @click="openChunk(c)">
                      <span class="citation-no">[{{ i + 1 }}]</span>
                      <span class="citation-title">{{ c.docTitle }}</span>
                      <span class="muted small">第 {{ c.seq }} 片</span>
                    </button>
                  </div>
                </div>
              </template>
            </div>

            <div v-if="live.active || live.answer" class="msg msg-assistant">
              <div class="bubble assistant-bubble streaming">
                <div class="bubble-head">
                  <span class="tag tag-warn">{{ live.running ? '生成中' : '本次回答' }}</span>
                  <span v-if="live.retrieval" class="muted small">
                    召回 {{ live.retrieval.total }} 条 · {{ live.retrieval.degraded ? '本地降级' : 'RAGFlow 向量' }}
                  </span>
                </div>
                <div v-if="live.tools.length" class="tools">
                  <span v-for="(tool, i) in live.tools" :key="`live-${i}`" class="tool"
                        :title="argsText(tool.args)">
                    <span class="tool-name">{{ tool.label || tool.name }}</span>
                    <span class="num">{{ tool.resultCount ?? 0 }} 条</span>
                    <span v-if="tool.elapsedMs" class="muted num">{{ tool.elapsedMs }} ms</span>
                  </span>
                </div>
                <div v-if="live.answer" class="answer" v-html="renderText(live.answer)"></div>
                <div v-else-if="live.running" class="typing"><span></span><span></span><span></span></div>
                <div v-if="live.citations.length" class="citations">
                  <span class="citations-title">引用</span>
                  <button v-for="(c, i) in live.citations" :key="`live-c-${i}`" class="citation"
                          @click="openChunk(c)">
                    <span class="citation-no">[{{ i + 1 }}]</span>
                    <span class="citation-title">{{ c.docTitle }}</span>
                    <span class="muted small">第 {{ c.seq }} 片</span>
                  </button>
                </div>
                <div v-if="live.hint" class="hint">
                  <span>{{ live.hint.text }}</span>
                  <router-link v-if="live.hint.url" :to="hintRoute(live.hint.url)" class="btn btn-link">去工单中心</router-link>
                </div>
              </div>
            </div>
          </div>

          <div class="composer">
            <textarea v-model="question" rows="2" :disabled="!canSend"
                      :placeholder="composerPlaceholder"
                      @keydown.ctrl.enter.prevent="send" @keydown.meta.enter.prevent="send"></textarea>
            <div class="composer-bar">
              <select v-model="scopeDept" :disabled="!canSend" style="width: 140px">
                <option :value="null">默认本部门</option>
                <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ d.name }}</option>
              </select>
              <select v-model="scopeType" :disabled="!canSend" style="width: 130px">
                <option :value="null">全部类型</option>
                <option v-for="t in TASK_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
              </select>
              <label class="check">
                <input v-model="allowTools" type="checkbox" :disabled="!canSend" />
                允许查询工单与统计
              </label>
              <span class="muted small">Ctrl/⌘ + Enter 发送，问题不超过 1000 字</span>
              <span class="spacer"></span>
              <button v-if="live.running" class="btn" @click="stopAsk">停止</button>
              <button class="btn btn-primary" :disabled="!canSend || !question.trim()" @click="send">
                {{ live.running ? '生成中…' : '发送' }}
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <BaseModal v-if="chunkOpen" :title="chunkView ? `${chunkView.docTitle} · 第 ${chunkView.seq} 片` : '引用原文'"
               @close="chunkOpen = false">
      <div v-if="chunkLoading" class="empty">加载原文…</div>
      <div v-if="chunkError" class="form-error">{{ chunkError }}</div>
      <div v-if="chunkView" class="chunk-meta">
        <span class="tag">文档 #{{ chunkView.docId || '—' }}</span>
        <span v-if="chunkView.pageNo" class="tag">第 {{ chunkView.pageNo }} 页</span>
        <span v-if="chunkView.superseded" class="tag tag-warn">旧快照</span>
      </div>
      <div v-if="chunkView" class="chunk-body">{{ chunkView.content }}</div>
      <p class="muted small">引用来自知识库切片；切片重建后原引用仍可回看摘要。</p>
      <template #footer>
        <button class="btn" @click="chunkOpen = false">关闭</button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { askStream, chatApi, CHAT_SAMPLES, QUESTION_MAX, newIdempotencyKey } from '../api/chat.js'
import { kbApi } from '../api/kb.js'
import { deptApi } from '../api/org.js'
import { TASK_TYPES } from '../api/taskOptions.js'
import BaseModal from '../components/BaseModal.vue'

const sessions = ref([])
const activeId = ref(null)
const messages = ref([])
const deptOptions = ref([])
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const creating = ref(false)
const loadError = ref('')
const notice = ref('')

const question = ref('')
const scopeDept = ref(null)
const scopeType = ref(null)
const allowTools = ref(true)

const live = reactive({
  running: false,
  answer: '',
  retrieval: null,
  tools: [],
  citations: [],
  hint: null,
  done: null
})

const chunkOpen = ref(false)
const chunkLoading = ref(false)
const chunkError = ref('')
const chunkView = ref(null)

const scrollEl = ref(null)
let askController = null

const activeSession = computed(() => sessions.value.find((s) => s.id === activeId.value) || null)
const archived = computed(() => activeSession.value?.status === 'ARCHIVED')
const canSend = computed(() => Boolean(activeSession.value) && !archived.value && !live.running)

const composerPlaceholder = computed(() => {
  if (archived.value) return '会话已归档，不能再提问；请新建会话'
  return '例如：生产部这周还有几张单没验收？（Ctrl/⌘ + Enter 发送）'
})

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

function modelLabel(message) {
  if (message.modelName === 'RULE') return '规则回答（降级）'
  return message.modelName || '模型回答'
}

function modelClass(message) {
  return message.modelName === 'RULE' ? 'tag-warn' : 'tag-ok'
}

function toolsOf(message) {
  const list = message.toolCalls
  return Array.isArray(list) ? list : []
}

function citationsOf(message) {
  const list = message.citations
  return Array.isArray(list) ? list : []
}

function argsText(args) {
  if (!args) return ''
  try {
    return JSON.stringify(args)
  } catch {
    return String(args)
  }
}

/** 后端 hint 给的是 hash 链接（#/tasks），router-link 只认路由 path */
function hintRoute(url) {
  return String(url || '').replace(/^#/, '') || '/tasks'
}

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 回答是纯文本，只做加粗与引用编号高亮，避免把模型输出当 HTML 执行 */
function renderText(text) {
  return escapeHtml(text)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\[(\d{1,2})\]/g, '<span class="cite-ref">[$1]</span>')
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollEl.value) {
      scrollEl.value.scrollTop = scrollEl.value.scrollHeight
    }
  })
}

watch(messages, scrollToBottom, { deep: true })
watch(() => [live.answer, live.tools.length], scrollToBottom)

async function loadDepts() {
  try {
    deptOptions.value = flatten(await deptApi.tree())
  } catch {
    deptOptions.value = []
  }
}

async function loadSessions() {
  loadingSessions.value = true
  loadError.value = ''
  try {
    sessions.value = await chatApi.sessions()
    if (activeId.value && !sessions.value.some((s) => s.id === activeId.value)) {
      activeId.value = null
      messages.value = []
    }
    if (!activeId.value && sessions.value.length) {
      await selectSession(sessions.value[0])
    }
  } catch (e) {
    loadError.value = e.message
  } finally {
    loadingSessions.value = false
  }
}

async function loadMessages(id) {
  loadingMessages.value = true
  try {
    messages.value = await chatApi.messages(id)
  } catch (e) {
    loadError.value = e.message
    messages.value = []
  } finally {
    loadingMessages.value = false
  }
}

async function createSession(silent = false) {
  creating.value = true
  loadError.value = ''
  try {
    const session = await chatApi.create({
      title: null,
      scopeType: 'DEPT',
      scopeId: scopeDept.value || null
    })
    sessions.value = [session, ...sessions.value.filter((s) => s.id !== session.id)]
    activeId.value = session.id
    messages.value = []
    if (!silent) {
      notice.value = ''
    }
    return session
  } catch (e) {
    if (!silent) {
      loadError.value = e.message
    }
    return null
  } finally {
    creating.value = false
  }
}

async function selectSession(session) {
  if (live.running) {
    stopAsk()
  }
  activeId.value = session.id
  live.answer = ''
  live.tools = []
  live.citations = []
  live.retrieval = null
  live.hint = null
  notice.value = ''
  await loadMessages(session.id)
  scrollToBottom()
}

async function archiveSession(session) {
  try {
    const updated = await chatApi.archive(session.id)
    const index = sessions.value.findIndex((s) => s.id === session.id)
    if (index >= 0) {
      sessions.value[index] = updated
    }
    if (activeId.value === session.id) {
      messages.value = await chatApi.messages(session.id)
    }
    notice.value = '会话已归档，历史仍可回看，但不能再提问'
  } catch (e) {
    loadError.value = e.message
  }
}

async function askSample(sample) {
  question.value = sample
  if (!activeSession.value) {
    await createSession(true)
  }
  await send()
}

async function send() {
  if (!canSend.value) return
  const text = question.value.trim()
  if (!text) return
  if (text.length > QUESTION_MAX) {
    notice.value = `问题不超过 ${QUESTION_MAX} 字`
    return
  }
  notice.value = ''
  const sessionId = activeId.value
  const key = newIdempotencyKey()

  messages.value.push({
    id: `local-user-${Date.now()}`,
    role: 'USER',
    roleLabel: '提问',
    content: text,
    citations: [],
    toolCalls: [],
    createdAt: new Date().toISOString()
  })
  question.value = ''
  Object.assign(live, {
    running: true,
    answer: '',
    retrieval: null,
    tools: [],
    citations: [],
    hint: null,
    done: null
  })
  scrollToBottom()

  askController = new AbortController()
  try {
    await askStream(sessionId, {
      question: text,
      scope: {
        deptId: scopeDept.value || null,
        taskTypes: scopeType.value ? [scopeType.value] : null
      },
      allowTools: allowTools.value
    }, {
      retrieval: (data) => {
        live.retrieval = data
      },
      toolCall: (data) => {
        if (data) live.tools.push(data)
      },
      delta: (chunk) => {
        live.answer += chunk
      },
      citations: (list) => {
        live.citations = list || []
      },
      hint: (data) => {
        live.hint = data
      },
      done: (data) => {
        live.done = data
      },
      error: (payload) => {
        notice.value = `${payload.code || '提问失败'}：${payload.message || '回答失败'}`
      }
    }, { signal: askController.signal, idempotencyKey: key })
  } catch (e) {
    if (e.name !== 'AbortError') {
      notice.value = e.message
    }
  } finally {
    askController = null
    live.running = false
    await refreshAfterAsk(sessionId)
  }
}

/** 回答落库后重载消息与会话列表，页面重开不需要重跑模型也能还原引用与工具记录 */
async function refreshAfterAsk(sessionId) {
  if (activeId.value === sessionId) {
    await loadMessages(sessionId)
  }
  live.answer = ''
  live.tools = []
  live.citations = []
  live.retrieval = null
  live.hint = null
  live.done = null
  try {
    sessions.value = await chatApi.sessions()
    if (activeId.value && !sessions.value.some((s) => s.id === activeId.value)) {
      activeId.value = null
    }
  } catch {
    // 列表刷新失败不影响本轮回答
  }
}

function stopAsk() {
  if (askController) {
    askController.abort()
    askController = null
    notice.value = '已停止生成，本轮回答未保存'
  }
}

async function openChunk(citation) {
  chunkOpen.value = true
  chunkLoading.value = true
  chunkError.value = ''
  chunkView.value = {
    docId: citation.docId,
    docTitle: citation.docTitle,
    seq: citation.seq,
    pageNo: citation.pageNo,
    content: citation.excerpt || '',
    superseded: false
  }
  try {
    const detail = await kbApi.chunk(citation.chunkId)
    if (detail) {
      chunkView.value = detail
    }
  } catch (e) {
    chunkError.value = `原文暂不可取（${e.message}），以下为引用摘要`
  } finally {
    chunkLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadDepts(), loadSessions()])
})

onBeforeUnmount(() => {
  stopAsk()
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
  grid-template-columns: 268px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
}

.side {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: calc(100vh - 190px);
  overflow: auto;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px 8px;
}

.side-head h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.side-empty {
  padding: 28px 10px;
  font-size: 12px;
  line-height: 1.7;
}

.session {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.session:hover {
  background: #F7F8F9;
}

.session.active {
  background: #F2F3F5;
  border-color: var(--divider);
}

.session-main {
  min-width: 0;
  flex: 1;
}

.session-title {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 4px;
  color: var(--txt-3);
  font-size: 11px;
}

.session-op {
  flex: none;
  font-size: 12px;
  visibility: hidden;
}

.session:hover .session-op,
.session.active .session-op {
  visibility: visible;
}

.chat {
  min-height: calc(100vh - 190px);
  max-height: calc(100vh - 190px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 40px 24px;
}

.empty-title {
  margin: 0;
  font-size: 15px;
  color: var(--txt);
}

.samples {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
  width: min(560px, 100%);
}

.sample {
  padding: 10px 12px;
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

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--divider);
  flex: none;
}

.chat-title {
  font-size: 14px;
  font-weight: 600;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: #FCFCFD;
}

.msg {
  display: flex;
  flex-direction: column;
  max-width: 100%;
}

.msg-user {
  align-items: flex-end;
}

.msg-assistant {
  align-items: flex-start;
}

.bubble {
  max-width: min(760px, 92%);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.7;
}

.user-bubble {
  background: var(--txt);
  color: #fff;
  white-space: pre-wrap;
}

.assistant-bubble {
  background: var(--surface);
  border: 1px solid var(--divider);
  box-shadow: var(--shadow-card);
}

.bubble-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.answer {
  white-space: pre-wrap;
  word-break: break-word;
}

.answer :deep(strong) {
  font-weight: 600;
}

.answer :deep(.cite-ref) {
  color: var(--info);
  font-family: var(--mono);
  font-size: 12px;
}

.tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--divider);
}

.tool {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border: 1px solid var(--divider);
  border-radius: 12px;
  font-size: 11px;
  color: var(--txt-2);
  cursor: default;
}

.tool-name {
  font-weight: 500;
  color: var(--txt);
}

.citations {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.citations-title {
  font-size: 11px;
  color: var(--txt-3);
}

.citation {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  padding: 2px 9px;
  border: 1px solid var(--divider);
  border-radius: 12px;
  background: var(--surface);
  cursor: pointer;
  font-size: 11px;
}

.citation:hover {
  border-color: var(--info);
}

.citation-no {
  color: var(--info);
  font-family: var(--mono);
}

.citation-title {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid rgba(10, 108, 255, 0.24);
  border-radius: var(--radius-sm);
  background: rgba(10, 108, 255, 0.05);
  font-size: 12px;
  color: var(--txt-2);
}

.msg-meta {
  margin-top: 3px;
  font-size: 10px;
  color: var(--txt-3);
}

.typing {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;
}

.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--txt-3);
  animation: blink 1.2s infinite ease-in-out;
}

.typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%, 80%, 100% { opacity: 0.25; }
  40% { opacity: 1; }
}

.composer {
  flex: none;
  border-top: 1px solid var(--divider);
  padding: 10px 14px 12px;
  background: var(--surface);
}

.composer textarea {
  width: 100%;
  resize: none;
  min-height: 54px;
}

.composer-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--txt-2);
  cursor: pointer;
}

.check input {
  height: auto;
}

.spacer {
  flex: 1;
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

  .side,
  .chat {
    max-height: none;
  }

  .chat {
    min-height: 520px;
  }

  .side {
    max-height: 260px;
  }

  .samples {
    grid-template-columns: 1fr;
  }
}
</style>
