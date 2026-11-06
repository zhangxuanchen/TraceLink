<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">工单助手</h2>
      </div>
    </div>

    <div v-if="loadError" class="form-error">{{ loadError }}</div>
    <div v-if="notice" class="notice">
      <span class="tag tag-warn">提示</span>
      <span>{{ notice }}</span>
      <button class="btn-link" @click="notice = ''">关闭</button>
    </div>

    <div class="layout">
      <div class="card chat">
        <div v-if="!activeSession" class="empty chat-empty">
          <p class="empty-title">{{ creating || loadingSessions ? '正在准备会话…' : '会话暂不可用' }}</p>
          <p class="muted">系统会自动保持一条会话历史，直接提问即可。</p>
        </div>

        <template v-else>
          <div class="chat-head">
            <div class="chat-title">{{ activeSession.title }}</div>
            <button class="btn btn-ghost" :disabled="live.running || messages.length === 0"
                    @click="onClear">清理上下文</button>
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
                <div v-if="live.clarify.length" class="clarify-suggestions">
                  <span class="clarify-label">请选择澄清方向：</span>
                  <button
                    v-for="(q, i) in live.clarify"
                    :key="i"
                    class="clarify-btn"
                    :title="'点击填入，也可直接修改'"
                    @click="fillClarify(q)"
                  >{{ q }}</button>
                </div>
              </div>
            </div>
          </div>

          <div class="composer">
            <textarea v-model="question" rows="2" :disabled="!canSend"
                      :placeholder="composerPlaceholder"
                      @keydown="onComposerKeydown"></textarea>
            <div class="composer-bar">
              <label class="check">
                <input v-model="allowTools" type="checkbox" :disabled="!canSend" />
                允许查询工单与统计
              </label>
              <span class="muted small">Enter 发送，Shift + Enter 换行，问题不超过 1000 字</span>
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
import { marked } from 'marked'
import { askStream, chatApi, QUESTION_MAX, newIdempotencyKey } from '../api/chat.js'
import { kbApi } from '../api/kb.js'
import BaseModal from '../components/BaseModal.vue'

const activeSession = ref(null)
const messages = ref([])
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const creating = ref(false)
const loadError = ref('')
const notice = ref('')

const question = ref('')
const allowTools = ref(true)

const live = reactive({
  active: false,
  running: false,
  answer: '',
  retrieval: null,
  tools: [],
  citations: [],
  hint: null,
  clarify: [],
  done: null
})

const chunkOpen = ref(false)
const chunkLoading = ref(false)
const chunkError = ref('')
const chunkView = ref(null)

const scrollEl = ref(null)
let askController = null

const canSend = computed(() => Boolean(activeSession.value) && !live.running)

const composerPlaceholder = computed(() => '例如：生产部这周还有几张单没验收？（Enter 发送，Shift + Enter 换行）')

function fmtTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
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

/** 点击澄清建议按钮 → 填入 composer，让用户可以再改或直接发 */
function fillClarify(q) {
  question.value = String(q || '')
  // 填完后自动聚焦到输入框末尾
  const ta = document.querySelector('.composer textarea')
  if (ta) {
    ta.focus()
    ta.setSelectionRange(ta.value.length, ta.value.length)
  }
  // 填完清空建议，避免用户误以为点了就会自动发
  live.clarify = []
}

/** Markdown 渲染：用 marked 做 GFM 解析（支持表格/任务列表/嵌套/自动链接/删除线），
 *  引用编号 [1] 保留自定义高亮样式。marked 内部默认 escape HTML，不信任模型输出里的标签。 */
function renderText(text) {
  if (!text) return ''
  marked.setOptions({ gfm: true, breaks: true, headerIds: false })
  const html = marked.parse(String(text))
  return html.replace(/\[(\d{1,2})\]/g, '<span class="cite-ref">[$1]</span>')
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollEl.value) {
      scrollEl.value.scrollTop = scrollEl.value.scrollHeight
    }
  })
}

watch(messages, scrollToBottom, { deep: true })
watch(() => [live.active, live.answer, live.tools.length], scrollToBottom)

/** 工单助手只保留一条会话：优先复用最近未归档的，没有就新建一条并一直续用 */
async function ensureSession() {
  loadingSessions.value = true
  creating.value = true
  loadError.value = ''
  try {
    const list = await chatApi.sessions()
    const usable = (list || []).find((item) => item.status !== 'ARCHIVED')
    const session = usable || await chatApi.create({
      title: null,
      scopeType: 'DEPT',
      scopeId: null
    })
    await loadMessages(session.id)
    activeSession.value = session
    scrollToBottom()
  } catch (e) {
    loadError.value = e.message
    activeSession.value = null
  } finally {
    loadingSessions.value = false
    creating.value = false
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

async function askSample(sample) {
  question.value = sample
  if (!activeSession.value) {
    await ensureSession()
  }
  await send()
}

/** Enter 发送，Shift + Enter 换行；输入法组合中的回车不触发发送 */
function onComposerKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  send()
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
  const sessionId = activeSession.value.id
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
    active: true,
    running: true,
    answer: '',
    retrieval: null,
    tools: [],
    citations: [],
    hint: null,
    clarify: [],
    done: null
  })
  scrollToBottom()

  askController = new AbortController()
  try {
    await askStream(sessionId, {
      question: text,
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
      clarify: (questions) => {
        live.clarify = questions || []
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

/** 回答落库后重载消息与会话信息，页面重开不需要重跑模型也能还原引用与工具记录 */
async function refreshAfterAsk(sessionId) {
  if (activeSession.value?.id === sessionId) {
    await loadMessages(sessionId)
  }
  live.answer = ''
  live.active = false
  live.tools = []
  live.citations = []
  live.retrieval = null
  live.hint = null
  live.done = null
  try {
    const list = await chatApi.sessions()
    const current = (list || []).find((item) => item.id === sessionId)
    if (current) activeSession.value = current
  } catch {
    // 会话信息刷新失败不影响本轮回答
  }
}

function stopAsk() {
  if (askController) {
    askController.abort()
    askController = null
    notice.value = '已停止生成，本轮回答未保存'
  }
}

async function onClear() {
  if (!activeSession.value || live.running) return
  if (!confirm('清空当前会话的全部历史消息，系统提示与工具保持不变。确认？')) return
  const sessionId = activeSession.value.id
  try {
    const view = await chatApi.clear(sessionId)
    messages.value = []
    Object.assign(live, { active: false, running: false, answer: '', retrieval: null, tools: [], citations: [], hint: null, clarify: [], done: null })
    if (view) activeSession.value = { ...activeSession.value, title: view.title || '新会话', msgCount: view.msgCount ?? 0 }
    notice.value = '上下文已清空，继续提问即可'
  } catch (e) {
    notice.value = `清理失败：${e.message}`
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
  await ensureSession()
})

onBeforeUnmount(() => {
  stopAsk()
})
</script>

<style scoped>
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
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
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

.answer :deep(em) {
  font-style: italic;
}

.answer :deep(h1),
.answer :deep(h2),
.answer :deep(h3),
.answer :deep(h4) {
  font-weight: 600;
  margin: 10px 0 6px;
  line-height: 1.4;
}

.answer :deep(h1) { font-size: 15px; }
.answer :deep(h2) { font-size: 14px; }
.answer :deep(h3) { font-size: 13px; }
.answer :deep(h4) { font-size: 12px; }

.answer :deep(p) {
  margin: 4px 0;
}

.answer :deep(ul),
.answer :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.answer :deep(ul) { list-style: disc; }
.answer :deep(ol) { list-style: decimal; }

.answer :deep(li) {
  margin: 1px 0;
}

.answer :deep(li > input[type="checkbox"]) {
  margin-right: 4px;
  vertical-align: middle;
}

.answer :deep(blockquote) {
  margin: 6px 0;
  padding: 4px 10px;
  border-left: 3px solid var(--divider);
  color: var(--txt-2);
  background: rgba(0, 0, 0, 0.02);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.answer :deep(a) {
  color: var(--info);
  text-decoration: underline;
}

.answer :deep(code) {
  font-family: var(--mono);
  font-size: 12px;
  padding: 1px 4px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.06);
}

.answer :deep(pre) {
  margin: 6px 0;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: #F6F8FA;
  border: 1px solid var(--divider);
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.6;
}

.answer :deep(pre code) {
  background: transparent;
  padding: 0;
  font-size: inherit;
}

.answer :deep(table) {
  border-collapse: collapse;
  margin: 6px 0;
  font-size: 12px;
  width: 100%;
}

.answer :deep(th),
.answer :deep(td) {
  border: 1px solid var(--divider);
  padding: 5px 8px;
  text-align: left;
}

.answer :deep(th) {
  background: #F6F8FA;
  font-weight: 600;
}

.answer :deep(hr) {
  border: none;
  border-top: 1px solid var(--divider);
  margin: 10px 0;
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

.clarify-suggestions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px dashed rgba(10, 108, 255, 0.4);
  border-radius: var(--radius-sm);
  background: rgba(10, 108, 255, 0.04);
}

.clarify-label {
  font-size: 12px;
  color: var(--txt-2);
  margin-right: 4px;
}

.clarify-btn {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 14px;
  border: 1px solid rgba(10, 108, 255, 0.3);
  background: #fff;
  color: #0a6cff;
  cursor: pointer;
  transition: all 0.15s;
  max-width: 280px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.clarify-btn:hover {
  background: rgba(10, 108, 255, 0.08);
  border-color: #0a6cff;
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
  .chat {
    min-height: 520px;
    max-height: none;
  }
}
</style>
