import request from './request'

// 学生
export function applyRefund(orderId, reason, itemIds) {
  return request.post(`/orders/${orderId}/refund`, { reason, itemIds })
}

export function myRefunds() {
  return request.get('/my/refunds')
}

// 商家
export function merchantRefunds(status) {
  return request.get('/merchant/refunds', { params: { status } })
}

export function approveMerchantRefund(id) {
  return request.post(`/merchant/refunds/${id}/approve`, null, { timeout: 40000 })
}

export function rejectMerchantRefund(id, remark) {
  return request.post(`/merchant/refunds/${id}/reject`, null, { params: { remark } })
}

// 管理员：只读记录，退款审核在商家端完成
export function adminRefunds(status) {
  return request.get('/admin/refunds', { params: { status } })
}
