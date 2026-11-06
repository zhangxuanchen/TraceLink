import { request } from './http.js'

/** 工单中心的「我的」视图、异步任务与账号角色，对应 desk/job/settings 三个后端入口；审计与参数的页面已下线，接口保留在服务端 */
export const deskApi = {
  get: () => request({ url: '/desk', method: 'get' })
}

export const jobApi = {
  page: (params) => request({ url: '/jobs', method: 'get', params }),
  detail: (id) => request({ url: `/jobs/${id}`, method: 'get' })
}

export const settingsApi = {
  configs: () => request({ url: '/configs', method: 'get' }),
  updateConfig: (key, value) => request({
    url: `/configs/${encodeURIComponent(key)}`,
    method: 'put',
    data: { value }
  }),
  gateRules: () => request({ url: '/gate-rules', method: 'get' }),
  updateGateRule: (id, data) => request({ url: `/gate-rules/${id}`, method: 'put', data }),
  roles: () => request({ url: '/roles', method: 'get' }),
  accounts: () => request({ url: '/accounts', method: 'get' }),
  updateAccountRoles: (employeeId, roles) => request({
    url: `/accounts/${employeeId}/roles`,
    method: 'put',
    data: { roles }
  })
}

export const JOB_STATUS_CLASS = {
  DONE: 'tag-ok',
  RUNNING: 'tag-warn',
  PENDING: 'tag-warn',
  FAILED: 'tag-bad'
}

/** 轮询任务直到结束；关掉浏览器后重新打开仍能从 /jobs 看到同一条记录 */
export async function waitJob(id, { timeout = 180000, interval = 1200 } = {}) {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    const job = await jobApi.detail(id)
    if (job.status === 'DONE' || job.status === 'FAILED') return job
    await new Promise((resolve) => setTimeout(resolve, interval))
  }
  throw new Error('任务仍在执行，请稍后在任务列表里查看结果')
}
