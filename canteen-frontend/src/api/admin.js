import request from './request'

// 管理员向某档口商家发 AI 生成的提醒
export function merchantNotify(stallId, topic) {
  return request.post('/admin/ai/merchant-notify', null, { params: { stallId, topic } })
}

// 管理员 AI 生成公告文案（标题 + 正文）
export function noticeGenerate(topic) {
  return request.post('/admin/ai/notice-generate', null, { params: { topic } })
}

// 商家沟通中心：全部档口（含权重档位、未读、最近往来）
export function merchantCommStalls() {
  return request.get('/admin/merchant-comm/stalls')
}

// 单个档口的完整双向线程
export function merchantCommThread(stallId) {
  return request.get(`/admin/merchant-comm/stalls/${stallId}/thread`)
}

// 管理员向商家发反馈/通知（AI 编辑好文案后发送）
export function merchantCommSend(stallId, data) {
  return request.post(`/admin/merchant-comm/stalls/${stallId}/send`, data)
}

// 敏感词自动屏蔽开关：读取/设置
export function moderationAutoBlock() {
  return request.get('/admin/moderation/auto-block')
}
export function setModerationAutoBlock(enabled) {
  return request.put('/admin/moderation/auto-block', { enabled })
}
