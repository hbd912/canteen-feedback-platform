import request from './request'

export function pageDishes(params) {
  return request.get('/dishes', { params })
}

/**
 * 拉某档口的全部菜品（后端每页上限 100，这里自动翻页）。
 * 菜单/发券选菜/档口详情统一用它，保证新上架或超过 100 道菜时不会被截断。
 */
export async function listAllDishes(stallId, params = {}) {
  const all = []
  for (let current = 1; current <= 50; current++) {
    const res = await pageDishes({ ...params, stallId, current, size: 100 })
    const data = res.data || {}
    const records = data.records || []
    all.push(...records)
    const total = Number(data.total || 0)
    if (!records.length || (total > 0 && all.length >= total)) break
  }
  return all
}

export function getDishDetail(id) {
  return request.get(`/dishes/${id}`)
}

export function listCategories() {
  return request.get('/dishes/categories')
}

export function createDish(data) {
  return request.post('/dishes', data)
}

export function updateDish(id, data) {
  return request.put(`/dishes/${id}`, data)
}

export function deleteDish(id) {
  return request.delete(`/dishes/${id}`)
}

// 商家：图片/文字 → 菜品上架草稿（不落库，商家确认后才创建）
export function aiDishDraft(data) {
  return request.post('/dishes/ai-draft', data, { timeout: 90000 })
}
