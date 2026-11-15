import request from './request'

export function pageUsers(params) {
  return request.get('/users', { params })
}

export function pageMerchants(params) {
  return request.get('/users/merchants', { params })
}

export function updateUserStatus(id, status) {
  return request.put(`/users/${id}/status`, null, { params: { status } })
}

/** 学生 AI 画像（口味/常点/消费/情绪记录 + AI 总结），管理端抽屉用 */
export function getUserAiProfile(id) {
  return request.get(`/users/${id}/ai-profile`)
}
