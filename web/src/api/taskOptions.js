export const TASK_TYPES = [
  { value: 'PATROL', label: '巡检' },
  { value: 'QUALITY', label: '质量' },
  { value: 'PLAN', label: '计划' },
  { value: 'EQUIP', label: '设备' },
  { value: 'CUSTOMER', label: '客户' },
  { value: 'SAFETY', label: '安全' },
  { value: 'OTHER', label: '其他' }
]

export const TASK_STATUSES = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待接单' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'BLOCKED', label: '受阻' },
  { value: 'SUBMITTED', label: '待验收' },
  { value: 'DONE', label: '已验收' },
  { value: 'CANCELLED', label: '已取消' }
]

export const CHECKIN_TYPES = [
  { value: 'PROGRESS', label: '进展' },
  { value: 'RESULT', label: '结果' },
  { value: 'ISSUE', label: '问题' }
]

export const RESCHEDULE_REASONS = [
  { value: 'EXTERNAL', label: '外部条件变化' },
  { value: 'CHANGE', label: '需求变更' },
  { value: 'STAFFING', label: '人手不足' },
  { value: 'ESTIMATE', label: '工期评估偏差' }
]

export const PRIORITIES = [
  { value: 0, label: '紧急' },
  { value: 1, label: '高' },
  { value: 2, label: '普通' },
  { value: 3, label: '低' }
]

export function statusClass(status) {
  if (status === 'DONE') return 'tag-ok'
  if (status === 'CANCELLED') return ''
  if (status === 'BLOCKED') return 'tag-bad'
  return 'tag-warn'
}

/** 后端给 "2026-11-08T18:00:00"，datetime-local 只认到分钟 */
export function toInputDateTime(value) {
  return value ? String(value).slice(0, 16) : ''
}

/** 表单里的分钟值补成后端可解析的 ISO 时间 */
export function toApiDateTime(value) {
  if (!value) return null
  return value.length === 16 ? `${value}:00` : value
}

export function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

export function typeLabel(value, list = TASK_TYPES) {
  return list.find((item) => item.value === value)?.label || value
}

export function splitLines(text) {
  return String(text || '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}
