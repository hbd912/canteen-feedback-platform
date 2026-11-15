import request from './request'

export function auditFeed(params) {
  return request.get('/admin/audit/feed', { params })
}

export function auditStats(days = 7) {
  return request.get('/admin/audit/stats', { params: { days } })
}
