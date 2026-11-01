import { request, TOKEN_KEY } from './http.js'

/** 会话平台接口（CHT-1/2/4），与 ChatController 对齐 */
export const chatApi = {
  sessions: (limit = 50) => request({ url: '/chat/sessions', method: 'get', params: { limit } }),
  create: (data) => request({ url: '/chat/sessions', method: 'post', data }),
  archive: (id) => request({ url: `/chat/sessions/${id}/archive`, method: 'post' }),
  messages: (id, limit = 100) => request({
    url: `/chat/sessions/${id}/messages`,
    method: 'get',
    params: { limit }
  })
}

export const CHAT_SCOPES = [
  { code: 'DEPT', label: '本部门' },
  { code: 'DEPT_TREE', label: '本部门及下级' },
  { code: 'ALL', label: '全公司' },
  { code: 'SELF', label: '仅本人' }
]

/** 提问文本上限与后端 ChatAskService 保持一致 */
export const QUESTION_MIN = 1
export const QUESTION_MAX = 1000

/** 首次进入会话页的示例问题：分别覆盖工单、统计、人员与知识四类工具 */
export const CHAT_SAMPLES = [
  '生产部这周还有几张单没验收？',
  '这个月各部门完成率和逾期情况怎么样？',
  '张厚生现在手里有多少在办工单？',
  '冲压工位毛刺超标，制度里怎么规定处理流程？'
]

/**
 * 提问事件流（CHT-3）：retrieval → tool_call… → delta… → citations → hint → done；
 * 模型不可用或失败时降级为规则回答，done.degraded=true。
 * 用 fetch 读流是因为 EventSource 不能带 Authorization 头、也不支持 POST 与幂等键。
 */
export async function askStream(sessionId, payload, handlers = {}, options = {}) {
  const { signal, idempotencyKey } = options
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY) || ''}`
  }
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey
  }
  const response = await fetch(`/api/v1/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
    signal
  })

  if (!response.ok) {
    let message = `提问失败（HTTP ${response.status}）`
    let code = ''
    try {
      const body = await response.json()
      code = body?.code || ''
      message = body?.message || message
    } catch {
      // 非 JSON 响应保持默认提示
    }
    throw Object.assign(new Error(message), { code, status: response.status })
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应，请改用知识库检索查看依据')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finished = false

  const dispatch = (event, data) => {
    if (event === 'retrieval') handlers.retrieval?.(data)
    else if (event === 'tool_call') handlers.toolCall?.(data)
    else if (event === 'delta') handlers.delta?.(data?.text || '')
    else if (event === 'citations') handlers.citations?.(data || [])
    else if (event === 'hint') handlers.hint?.(data)
    else if (event === 'done') {
      finished = true
      handlers.done?.(data)
    } else if (event === 'error') {
      finished = true
      handlers.error?.(data || {})
    }
  }

  const drain = (flush = false) => {
    let index = buffer.indexOf('\n\n')
    while (index >= 0) {
      const block = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      const event = /^event:(.*)$/m.exec(block)?.[1]?.trim() || 'message'
      const raw = block.split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart())
        .join('\n')
      let data = raw
      try {
        data = raw ? JSON.parse(raw) : null
      } catch {
        // 非 JSON 数据按字符串透传
      }
      dispatch(event, data)
      index = buffer.indexOf('\n\n')
    }
    if (flush && buffer.trim()) {
      const event = /^event:(.*)$/m.exec(buffer)?.[1]?.trim() || 'message'
      const raw = buffer.split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart())
        .join('\n')
      if (raw) {
        let data = raw
        try {
          data = JSON.parse(raw)
        } catch {
          // 保持字符串
        }
        dispatch(event, data)
      }
      buffer = ''
    }
  }

  try {
    while (!finished) {
      const { value, done } = await reader.read()
      if (done) {
        drain(true)
        break
      }
      buffer += decoder.decode(value, { stream: true })
      drain()
    }
  } finally {
    reader.cancel().catch(() => {})
  }
}

/** 幂等键：同一轮提问重试时复用，服务端不重复写消息也不重复计费 */
export function newIdempotencyKey() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `chat-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}
