import { request } from './http.js'
import { downloadFile } from './import.js'

export const exportApi = {
  list: () => request({ url: '/exports', method: 'get' }),
  create: (data) => request({ url: '/exports', method: 'post', data, timeout: 120000 }),
  createAsync: (data) => request({ url: '/exports', method: 'post', data, params: { async: true } }),
  download: (id, fallbackName) => downloadFile(`/api/v1/exports/${id}/download`, fallbackName)
}

/** 六种固定模板，与后端 ExportTemplate 一一对应（DESIGN 4.6） */
export const EXPORT_TEMPLATES = [
  { code: 'RATES', label: '部门与个人率表', desc: '两张表：部门与个人各自的七项率，口径同统计看板' },
  { code: 'DAILY', label: '日报回填', desc: '按日期区间一天一页，版式与源文件一致，可直接再导入' },
  { code: 'WEEKLY', label: '周报汇总', desc: '关键指标、部门对比、问题类型前十、新增与完成趋势' },
  { code: 'LEDGER', label: '工单台账', desc: '16 列全字段台账，含期限、进度、逾期与结果摘要' },
  { code: 'PERF', label: '绩效数据', desc: '人员产出、质量、及时、留痕四类指标与能力雷达' },
  { code: 'CHART', label: '统计图表', desc: '13 页图表数据底表，供线下复用与二次汇总' }
]

export const EXPORT_STATUS_CLASS = {
  DONE: 'tag-ok',
  RUNNING: 'tag-warn',
  PENDING: 'tag-warn',
  FAILED: 'tag-bad'
}
