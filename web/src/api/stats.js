import { request } from './http.js'

/** 四个统计入口与 StatsController 对齐，from/to 均为 yyyy-MM-dd */
export const statsApi = {
  overview: (params) => request({ url: '/stats/overview', method: 'get', params }),
  trend: (params) => request({ url: '/stats/trend', method: 'get', params }),
  process: (params) => request({ url: '/stats/process', method: 'get', params }),
  person: (params) => request({ url: '/stats/person', method: 'get', params })
}

/** 快捷区间：看板上最常用的四个视角 */
export const RANGE_PRESETS = [
  { key: 'week', label: '近 7 天', days: 6 },
  { key: 'month', label: '近 30 天', days: 29 },
  { key: 'quarter', label: '近 90 天', days: 89 },
  { key: 'custom', label: '自定义', days: null }
]

export function dayString(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function rangeOf(preset) {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - preset.days)
  return { from: dayString(start), to: dayString(end) }
}
