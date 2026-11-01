import { request } from './http.js'

export const gateApi = {
  map: (id, params) => request({ url: `/tasks/${id}/completion-map`, method: 'get', params }),
  matrix: (params) => request({ url: '/completion-map/matrix', method: 'get', params }),
  gaps: (params) => request({ url: '/completion-map/gaps', method: 'get', params }),
  matter: (taskId) => request({ url: `/completion-map/matter/${taskId}`, method: 'get' }),
  snapshot: (date) => request({
    url: '/completion-map/snapshot', method: 'post', params: date ? { date } : {}
  }),
  notifications: (params) => request({ url: '/notifications', method: 'get', params }),
  readNotification: (id) => request({ url: `/notifications/${id}/read`, method: 'post' })
}

export const TASK_TYPES = [
  { value: null, label: '全部类型' },
  { value: 'QUALITY', label: '质量' },
  { value: 'PATROL', label: '巡查' },
  { value: 'PLAN', label: '计划' },
  { value: 'CUSTOMER', label: '客户' },
  { value: 'EQUIP', label: '设备' },
  { value: 'SAFETY', label: '安全' },
  { value: 'OTHER', label: '其他' }
]
