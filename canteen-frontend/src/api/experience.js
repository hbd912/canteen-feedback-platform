import request from './request'

// 管理员：经验库
export function adminExperiences(params) {
  return request.get('/admin/experiences', { params })
}

export function searchExperiences(params) {
  return request.get('/admin/experiences/search', { params })
}

export function createExperience(data) {
  return request.post('/admin/experiences', data)
}

export function deleteExperience(id) {
  return request.delete(`/admin/experiences/${id}`)
}

export function reindexExperiences() {
  return request.post('/admin/experiences/reindex', null, { timeout: 120000 })
}

export function experienceStats() {
  return request.get('/admin/experiences/stats')
}
