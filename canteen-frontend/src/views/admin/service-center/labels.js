export const categories = [
  { value: 'EVALUATE', label: '评价相关' },
  { value: 'FEEDBACK', label: '投诉与反馈' },
  { value: 'RULE', label: '食堂规则' },
  { value: 'ORDER', label: '订单相关' },
  { value: 'OTHER', label: '其他问题' }
]
export const scenes = [
  { value: 'CUSTOMER_SERVICE', label: '客服会话' },
  { value: 'MERCHANT_ASSISTANT', label: '经营助理' },
  { value: 'REVIEW_REPLY', label: '评价回复' }
]
export const categoryLabel = value => categories.find(item => item.value === value)?.label || value || '其他问题'
export const sceneLabel = value => scenes.find(item => item.value === value)?.label || value || '通用'
export const sourceLabel = value => ({ ADMIN: '管理员录入', MANUAL: '人工回复', MERCHANT: '商家确认', HELPFUL: '商家标记有用', USER_FEEDBACK: '用户确认' }[value] || value || '人工确认')
export const shortTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '—'
export const senderLabel = value => ({ STUDENT: '学生', MERCHANT: '商家', AI: '小助手' }[value] || '消息')
export function recordStatus(row) {
  if (row.kind === 'SESSION') return Number(row.merchantUnread) > 0 ? '待商家处理' : row.status === 'OPEN' ? '进行中' : '已结束'
  if (row.hit === 0 || row.status === 'MISS') return '未匹配'
  if (row.helpful === 0 || row.status === 'UNHELPFUL') return '用户反馈无帮助'
  return '已回答'
}
export function recordStatusClass(row) {
  if (row.kind === 'SESSION') return Number(row.merchantUnread) > 0 ? 'warning' : ''
  return row.hit === 0 || row.helpful === 0 ? 'warning' : 'good'
}
