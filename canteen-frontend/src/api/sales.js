import request from './request'

export function getSalesOverview() {
  return request.get('/merchant/sales/overview')
}

export function getSalesSummary() {
  return request.get('/merchant/sales/summary')
}

export function getDailyIncome(days) {
  return request.get('/merchant/sales/daily', { params: { days } })
}

export function getTrendDishes() {
  return request.get('/merchant/sales/trend')
}

export function getOrderAnalysis(days) {
  return request.get('/merchant/sales/order-analysis', { params: { days } })
}

/** AI 销售分析：AI 选图表 + 解读，数据由后端真实统计 */
export function getAiSalesAnalysis(days) {
  return request.get('/merchant/sales/ai-analysis', { params: { days }, timeout: 90000 })
}

export function uploadImage(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/files/upload', form)
}
