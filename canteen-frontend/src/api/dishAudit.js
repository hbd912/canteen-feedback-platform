import request from './request'

/** 管理员：菜品审核列表 */
export function listDishAudits(params) {
  return request.get('/admin/dish-audits', { params })
}

/** 管理员：审核通过（菜品随即上架并通知商家） */
export function approveDishAudit(id) {
  return request.post(`/admin/dish-audits/${id}/approve`)
}

/** 管理员：审核驳回（原因必填，菜品保持下架并通知商家） */
export function rejectDishAudit(id, reason) {
  return request.post(`/admin/dish-audits/${id}/reject`, { reason })
}
