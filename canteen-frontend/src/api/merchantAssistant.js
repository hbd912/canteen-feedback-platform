import request from './request'

// 商家 ↔ 经营 Agent 对话（可选带图片与当前技能，AI 会识别图片内容再回答）
export function askAssistant(question, image, skill) {
  return request.post('/merchant/ai-assistant/ask', { question, image, skill }, { timeout: 60000 })
}

// 大脑事件日志（超管/商家可看）
export function brainLogs(limit = 50) {
  return request.get('/ai/brain/logs', { params: { limit } })
}

// 让大脑判断一个事件该分配给谁
export function brainDecide(data) {
  return request.post('/ai/brain/decide', data, { timeout: 30000 })
}

// 商家认为某条经营建议有用 → 写入经验库
export function assistantFeedback(data) {
  return request.post('/merchant/ai-assistant/feedback', data, { timeout: 40000 })
}
