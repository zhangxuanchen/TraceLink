import axios from 'axios'

export const TOKEN_KEY = 'tracelink.token'
export const USER_KEY = 'tracelink.user'

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const body = error.response?.data
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      if (!window.location.hash.startsWith('#/login')) {
        window.location.hash = '#/login'
      }
    }
    const message = body?.message
      || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请稍后重试')
    return Promise.reject(new Error(message))
  }
)

export async function request(config) {
  const response = await http.request(config)
  const body = response.data
  if (!body || body.code !== '0') {
    throw new Error(body?.message || '操作失败')
  }
  return body.data
}
