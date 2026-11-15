import request from './request'

// 发起余额充值（触发支付网关，mock 自动成功）
export function rechargeBalance(amount) {
  return request.post('/balance/recharge', { amount }, { timeout: 30000 })
}

export function getRechargeStatus(payNo) {
  return request.get('/balance/recharge/status', { params: { payNo } })
}

export function mockRechargeSuccess(payNo) {
  return request.post(`/balance/recharge/${payNo}/mock-success`)
}

export function listRecharges() {
  return request.get('/balance/records')
}

// 账户明细（充值/消费/退款流水）
export function listTransactions(limit) {
  return request.get('/balance/transactions', { params: { limit } })
}
