import { request } from './http.js'

export const taskApi = {
  page: (params) => request({ url: '/tasks', method: 'get', params }),
  counts: () => request({ url: '/tasks/counts', method: 'get' }),
  overdue: () => request({ url: '/tasks/overdue', method: 'get' }),
  blocked: () => request({ url: '/tasks/blocked', method: 'get' }),
  detail: (id) => request({ url: `/tasks/${id}`, method: 'get' }),
  timeline: (id) => request({ url: `/tasks/${id}/timeline`, method: 'get' }),
  create: (data) => request({ url: '/tasks', method: 'post', data }),
  dispatch: (id, data) => request({ url: `/tasks/${id}/dispatch`, method: 'post', data }),
  checkin: (id, data) => request({ url: `/tasks/${id}/checkin`, method: 'post', data }),
  submit: (id, data) => request({ url: `/tasks/${id}/submit`, method: 'post', data }),
  verify: (id, data) => request({ url: `/tasks/${id}/verify`, method: 'post', data }),
  reschedule: (id, data) => request({ url: `/tasks/${id}/reschedule`, method: 'post', data }),
  urge: (id) => request({ url: `/tasks/${id}/urge`, method: 'post' }),
  cancel: (id, data) => request({ url: `/tasks/${id}/cancel`, method: 'post', data }),
  closeIssue: (id, issueId) => request({ url: `/tasks/${id}/issues/${issueId}/close`, method: 'post' }),
  doneAction: (id, actionId) => request({ url: `/tasks/${id}/actions/${actionId}/done`, method: 'post' })
}
