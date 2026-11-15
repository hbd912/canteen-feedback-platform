import request from './request'

export function getOverview() {
  return request.get('/stats/overview')
}

export function getDishRanking(limit = 10, period = 'all') {
  return request.get('/stats/dish-ranking', { params: { limit, period } })
}

export function getStallGoodRate() {
  return request.get('/stats/stall-good-rate')
}

/** 美食双龙榜（数据看板中部主图） */
export function getDragonBoard(limit = 5) {
  return request.get('/stats/dragon-board', { params: { limit } })
}

export function getStallTop(limit = 3) {
  return request.get('/stats/stall-top', { params: { limit } })
}

export function getRatingTrend(days = 7) {
  return request.get('/stats/rating-trend', { params: { days } })
}

export function getDishScatter(limit = 0) {
  return request.get('/stats/dish-scatter', { params: { limit } })
}

export function getStallScatter() {
  return request.get('/stats/stall-scatter')
}

/** 数据看板聚合数据（管理端数据看板页使用，仅管理员） */
export function getDashboard(days = 30) {
  return request.get('/stats/dashboard', { params: { days } })
}
