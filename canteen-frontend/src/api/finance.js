import request from './request'

export function getFinanceSummary() {
  return request.get('/merchant/finance/summary')
}

export function getFinanceLedger() {
  return request.get('/merchant/finance/ledger')
}

export function withdraw(data) {
  return request.post('/merchant/finance/withdraw', data)
}

export function getAiReport() {
  return request.get('/merchant/finance/report', { timeout: 60000 })
}
