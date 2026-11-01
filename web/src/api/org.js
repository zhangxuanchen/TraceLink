import { request } from './http.js'

export const authApi = {
  login: (username, password) => request({ url: '/auth/login', method: 'post', data: { username, password } }),
  me: () => request({ url: '/auth/me', method: 'get' })
}

export const deptApi = {
  tree: () => request({ url: '/depts/tree', method: 'get' }),
  create: (data) => request({ url: '/depts', method: 'post', data }),
  update: (id, data) => request({ url: `/depts/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/depts/${id}`, method: 'delete' })
}

export const positionApi = {
  list: (deptId) => request({ url: '/positions', method: 'get', params: deptId ? { deptId } : {} }),
  create: (data) => request({ url: '/positions', method: 'post', data }),
  update: (id, data) => request({ url: `/positions/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/positions/${id}`, method: 'delete' })
}

export const employeeApi = {
  page: (params) => request({ url: '/employees', method: 'get', params }),
  options: () => request({ url: '/employees/options', method: 'get' }),
  detail: (id) => request({ url: `/employees/${id}`, method: 'get' }),
  create: (data) => request({ url: '/employees', method: 'post', data }),
  update: (id, data) => request({ url: `/employees/${id}`, method: 'put', data })
}
