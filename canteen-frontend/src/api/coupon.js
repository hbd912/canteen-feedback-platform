import request from './request'

export function myCoupons() {
  return request.get('/coupons/my')
}

export function availableCoupons(stallId) {
  return request.get('/coupons/available', { params: { stallId } })
}

export function claimCoupon(couponId) {
  return request.post(`/coupons/${couponId}/claim`)
}

/** 领取客服聊天里发来的券卡片 */
export function claimCouponOffer(offerId) {
  return request.post(`/coupons/offers/${offerId}/claim`)
}
