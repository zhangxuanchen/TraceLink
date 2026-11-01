import { request } from './http.js'

export const importApi = {
  list: () => request({ url: '/imports', method: 'get' }),
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request({
      url: '/imports',
      method: 'post',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 180000
    })
  },
  detail: (id) => request({ url: `/imports/${id}`, method: 'get' }),
  preview: (id, reanalyze = false) => request({ url: `/imports/${id}/preview`, method: 'get', params: { reanalyze }, timeout: 60000 }),
  updateRow: (id, rowId, data) => request({ url: `/imports/${id}/rows/${rowId}`, method: 'put', data }),
  confirm: (id) => request({ url: `/imports/${id}/confirm`, method: 'post', timeout: 120000 }),
  revoke: (id) => request({ url: `/imports/${id}`, method: 'delete' }),
  reportUrl: (id) => `/api/v1/imports/${id}/report.xlsx`,
  reports: (params) => request({ url: '/reports', method: 'get', params })
}

/** 受保护下载：带 token 拉二进制再交给浏览器保存 */
export async function downloadFile(url, fallbackName) {
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${localStorage.getItem('tracelink.token') || ''}` }
  })
  if (!response.ok) {
    throw new Error('下载失败，请确认权限或稍后重试')
  }
  const disposition = response.headers.get('Content-Disposition') || ''
  const matched = /filename\*=UTF-8''([^;]+)/.exec(disposition)
  const fileName = matched ? decodeURIComponent(matched[1]) : fallbackName
  const blob = await response.blob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  URL.revokeObjectURL(link.href)
  link.remove()
  return fileName
}
