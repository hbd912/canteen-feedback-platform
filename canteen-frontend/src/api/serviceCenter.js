import request from './request'

export function getKnowledge(params) {
  return request.get('/admin/customer-service/knowledge', { params })
}

export function getServiceRecords(params) {
  return request.get('/admin/customer-service/records', { params })
}

export function getAdminSessionMessages(id) {
  return request.get(`/admin/customer-service/sessions/${id}/messages`)
}

export function updateExperience(id, data) {
  return request.put(`/admin/experiences/${id}`, data)
}
