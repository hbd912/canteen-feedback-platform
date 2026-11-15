import request from './request'

// 管理端工作台：今日指标 + 待办聚合
export function getAdminWorkbench() {
  return request.get('/admin/workbench')
}

// AI 值班助理：读取缓存 / 重新整理。整理可能需要调用模型，单独放宽超时。
export function getAdminAiInsight() {
  return request.get('/admin/workbench/ai-insight', { timeout: 60000 })
}

export function refreshAdminAiInsight() {
  return request.post('/admin/workbench/ai-insight/refresh', null, { timeout: 60000 })
}

// 管理员明确「不用确认，直接处理」：把可代处理的敏感条目一并处理
export function handleAllAdminAiInsight() {
  return request.post('/admin/workbench/ai-insight/handle-all', null, { timeout: 60000 })
}

// 管理员选「只处理免审核的」：只自动处理非敏感项
export function handleAutoAdminAiInsight() {
  return request.post('/admin/workbench/ai-insight/handle-auto', null, { timeout: 60000 })
}

// 值班助理开场白：根据当前待办动态生成
export function getAdminAiGreeting() {
  return request.get('/admin/workbench/ai-insight/greeting', { timeout: 60000 })
}

// 工作台 AI 对话：基于当前研判回答“先处理哪些/哪些可安抚”等问题
export function askAdminAiInsight(question) {
  return request.post('/admin/workbench/ai-insight/ask', { question }, { timeout: 60000 })
}

// ---- 值班助理会话：服务端持久化，换浏览器/清缓存不丢 ----

export function listAdminAiSessions() {
  return request.get('/admin/workbench/ai-chat/sessions')
}

export function createAdminAiSession() {
  return request.post('/admin/workbench/ai-chat/sessions')
}

export function deleteAdminAiSession(id) {
  return request.delete(`/admin/workbench/ai-chat/sessions/${id}`)
}

export function getAdminAiSessionMessages(id) {
  return request.get(`/admin/workbench/ai-chat/sessions/${id}/messages`)
}

/** 全量同步当前会话消息（前端是消息形态的唯一来源，服务端存 JSON 快照） */
export function syncAdminAiSessionMessages(id, messages) {
  return request.post(`/admin/workbench/ai-chat/sessions/${id}/messages`, {
    messagesJson: JSON.stringify(messages)
  }, { timeout: 30000 })
}

// 全平台销售分析报告（管理员）：图表 + 可下载表格
export function getAdminSalesAnalysis(days) {
  return request.get('/admin/sales/analysis', { params: { days }, timeout: 90000 })
}
