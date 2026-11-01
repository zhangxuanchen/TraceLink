import { request } from './http.js'

/** 评价与建议接口（EVL-1/2、AI-1/2），与 EvaluationController、SuggestionController 对齐 */
export const reviewApi = {
  evaluations: {
    run: (data) => request({ url: '/evaluations/run', method: 'post', data, timeout: 60000 }),
    list: (params) => request({ url: '/evaluations', method: 'get', params }),
    detail: (id) => request({ url: `/evaluations/${id}`, method: 'get' }),
    appeal: (id, data) => request({ url: `/evaluations/${id}/appeal`, method: 'post', data })
  },
  suggestions: {
    run: (type) => request({
      url: '/ai/suggestions/run',
      method: 'post',
      params: type ? { type } : {},
      timeout: 60000
    }),
    list: (params) => request({ url: '/ai/suggestions', method: 'get', params }),
    detail: (id) => request({ url: `/ai/suggestions/${id}`, method: 'get' }),
    accept: (id) => request({ url: `/ai/suggestions/${id}/accept`, method: 'post', timeout: 30000 })
  }
}

export const EVAL_TARGETS = [
  { code: 'EMPLOYEE', label: '人员评价' },
  { code: 'TASK', label: '任务评价' }
]

/** 人员六维（DESIGN 4.8），顺序与页面展示一致 */
export const PERSON_DIMENSIONS = [
  { code: 'delivery', label: '交付力', desc: '完成率与按时率各半' },
  { code: 'evidence', label: '结果留痕', desc: '结果记录率与证据完整度' },
  { code: 'response', label: '响应力', desc: '逾期占在办比' },
  { code: 'solution', label: '解决力', desc: '关闭问题与复发情况' },
  { code: 'cooperation', label: '协办力', desc: '参与他人工单与跨部门协作' },
  { code: 'compliance', label: '规范性', desc: '日报与留痕' }
]

/** 任务四维：输出复盘卡 */
export const TASK_DIMENSIONS = [
  { code: 'importance', label: '重要度', desc: '优先级与客户关联' },
  { code: 'complexity', label: '复杂度', desc: '问题项、参与人与跨部门' },
  { code: 'health', label: '健康度', desc: '改期与催办次数' },
  { code: 'quality', label: '结果质量', desc: '退回与复发' }
]

export const EVIDENCE_LABELS = {
  created: '新建工单',
  due: '应完成',
  done: '已完成',
  open: '在办',
  overdue: '逾期',
  rejected: '被退回',
  onTimeRate: '按时率',
  evidenceRate: '留痕率',
  avgHours: '平均耗时（小时）',
  coAssignCount: '协办工单',
  taskId: '工单',
  taskNo: '工单号',
  deptName: '部门',
  issueIds: '问题项',
  assigneeIds: '参与人',
  delayCount: '改期次数',
  urgeCount: '催办次数',
  rejectCount: '退回次数',
  planEnd: '计划完成',
  overdueDays: '逾期天数',
  status: '状态',
  durationSamples: '耗时样本',
  sameTypeDoneCount: '同类完成数',
  candidates: '候选人',
  count: '出现次数',
  taskIds: '关联工单',
  deptId: '部门'
}

export const SUGGESTION_TYPES = [
  { code: 'DISPATCH', label: '派工建议', desc: '无主责按负载与成功率给候选' },
  { code: 'ACTION', label: '措施建议', desc: '召回历史措施并另起复盘单' },
  { code: 'DUE_DATE', label: '期限建议', desc: '按同类耗时中位数与八成位改期' },
  { code: 'ESCALATE', label: '升级建议', desc: '逾期或反复改期按上级链升级' },
  { code: 'POLICY_GAP', label: '制度缺口建议', desc: '同类问题 30 天内出现 3 次以上' }
]

export const SUGGESTION_STATUSES = [
  { code: 'PENDING', label: '待确认' },
  { code: 'ACCEPTED', label: '已采纳' },
  { code: 'IGNORED', label: '已忽略' }
]

export const SUGGESTION_TARGETS = [
  { code: 'TASK', label: '工单' },
  { code: 'DEPT', label: '部门' },
  { code: 'EMPLOYEE', label: '人员' }
]

export function suggestionTypeMeta(code) {
  return SUGGESTION_TYPES.find((item) => item.code === code) || { code, label: code, desc: '' }
}

export function suggestionStatusClass(status) {
  if (status === 'ACCEPTED') return 'tag-ok'
  if (status === 'IGNORED') return 'tag-warn'
  return ''
}

export function evalStatusClass(status) {
  if (status === 'PUBLISHED') return 'tag-ok'
  if (status === 'APPEALED') return 'tag-warn'
  return ''
}

export function fmtDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

export function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}
