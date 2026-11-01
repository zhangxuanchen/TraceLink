import { request, TOKEN_KEY } from './http.js'

export const kbApi = {
  documents: (params) => request({ url: '/kb/documents', method: 'get', params }),
  upload: (file, fields) => {
    const form = new FormData()
    form.append('file', file)
    if (fields.title) form.append('title', fields.title)
    if (fields.domain) form.append('domain', fields.domain)
    if (fields.deptId) form.append('deptId', String(fields.deptId))
    if (fields.visibility) form.append('visibility', fields.visibility)
    return request({
      url: '/kb/documents',
      method: 'post',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
  },
  sync: (id) => request({ url: `/kb/documents/${id}/sync`, method: 'get' }),
  reparse: (id) => request({ url: `/kb/documents/${id}/sync`, method: 'post', timeout: 30000 }),
  history: (deptId, limit = 100) => request({
    url: '/kb/documents/history',
    method: 'post',
    params: { limit, ...(deptId ? { deptId } : {}) },
    timeout: 180000
  }),
  search: (data) => request({ url: '/kb/search', method: 'post', data, timeout: 30000 }),
  chunk: (id) => request({ url: `/kb/chunks/${id}`, method: 'get' })
}

/** 知识域与后端 KbDomain 一一对应（DESIGN 4.7） */
export const KB_DOMAINS = [
  { code: 'HISTORY', label: '历史问题与措施' },
  { code: 'POLICY', label: '制度' },
  { code: 'PROCESS', label: '流程' },
  { code: 'CUSTOMER', label: '客户与项目' },
  { code: 'MEETING', label: '会议与汇报' },
  { code: 'OTHER', label: '其它' }
]

export const KB_STATUS_CLASS = {
  READY: 'tag-ok',
  PARSING: 'tag-warn',
  PENDING: 'tag-warn',
  FAILED: 'tag-bad'
}

export const KB_VISIBILITY = [
  { code: 'ALL', label: '全员可见' },
  { code: 'DEPT', label: '本部门可见' },
  { code: 'PRIVATE', label: '仅上传者可见' }
]

/**
 * 问答事件流：retrieved → delta… → citations → done，模型不可用时只发 error。
 * 用 fetch 读流是因为 EventSource 不能带 Authorization 头、也不支持 POST。
 */
export async function askStream(payload, handlers = {}, signal) {
  const response = await fetch('/api/v1/kb/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY) || ''}`
    },
    body: JSON.stringify(payload),
    signal
  })

  if (!response.ok) {
    let message = `问答失败（HTTP ${response.status}）`
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
    throw new Error('当前浏览器不支持流式响应，请改用检索查看原文')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finished = false

  const dispatch = (event, data) => {
    if (event === 'retrieved') handlers.retrieved?.(data)
    else if (event === 'delta') handlers.delta?.(data?.text || '')
    else if (event === 'citations') handlers.citations?.(data || [])
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
      const dataLines = block.split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart())
      const raw = dataLines.join('\n')
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
