import { request } from './http.js'

/** 模型配置：读当前火山方舟配置、保存 AK 与模型 ID、用已保存配置做一次连通性自检 */
export const aiApi = {
  config: () => request({ url: '/ai/config', method: 'get' }),
  updateConfig: (data) => request({ url: '/ai/config', method: 'put', data }),
  test: () => request({ url: '/ai/config/test', method: 'post' })
}
