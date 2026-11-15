import request from './request'

// 商家待办
export function listTodos() {
  return request.get('/merchant/todos')
}

// 商家待办聚合（新订单/退款/客服消息/评价 + 整改/差评）
export function listTodoSummary() {
  return request.get('/merchant/todos/summary')
}

export function doneTodo(id) {
  return request.put(`/merchant/todos/${id}/done`)
}

// 商家 AI 一键代理：自动处理待办（转异步，接口立即返回）
export function aiProcessTodos() {
  return request.post('/merchant/todos/ai-process', null, { timeout: 20000 })
}

// 商家 AI 一键代理：查询处理状态/结果（running / report / error）
export function getAiProcessStatus() {
  return request.get('/merchant/todos/ai-process/status')
}

// 商家档口顾客频次统计（新客/常客/老客分层 + 学生单量明细）
export function getStallCustomerStats() {
  return request.get('/merchant/stats/customers')
}

export function createInspectTodo(data) {
  return request.post('/merchant/todos', data)
}

// 商家留言
export function sendMessage(data) {
  return request.post('/merchant/messages', data)
}

export function listMyMessages() {
  return request.get('/merchant/messages')
}

export function listAllMessages(params) {
  return request.get('/merchant/messages/all', { params })
}

export function replyMessage(id, data) {
  return request.post(`/merchant/messages/${id}/reply`, data)
}
