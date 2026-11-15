/**
 * 退款 + 整单券 端到端测试：
 *  1) 商家发一张「整单 5 折券」，学生领券后下一单（两菜品）
 *  2) 整单券订单：部分退款必须被拒绝（否则会按原价多退）
 *  3) 整单券订单：整单退款金额 == 券后实付（order.totalAmount），不是原价合计
 *  4) 无券订单：部分退款仍可正常申请（回归，不误伤）
 */
const BASE = 'http://localhost:8080/api'
async function api(path, { method = 'GET', body, token } = {}) {
  const headers = {}
  if (token) headers.Authorization = 'Bearer ' + token
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  return res.json()
}
const iso = (ms) => new Date(ms).toISOString().slice(0, 19)
// 优惠券接口用 @JsonFormat("yyyy-MM-dd HH:mm:ss")，不能传 ISO
const dt = (ms) => {
  const d = new Date(ms)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
const results = []
const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
const num = (v) => Number(v || 0)

;(async () => {
  // 登录
  const sLogin = await api('/auth/login', { method: 'POST', body: { username: 'student01', password: '123456' } })
  const sToken = sLogin.data?.token
  const mLogin = await api('/auth/login', { method: 'POST', body: { username: '100012', password: '123456' } })
  const mToken = mLogin.data?.token
  if (!sToken || !mToken) { console.error('登录失败', sLogin, mLogin); process.exit(1) }

  const now = Date.now()
  // 1) 商家创建整单 5 折券
  const cRes = await api('/merchant/coupons', {
    method: 'POST', token: mToken,
    body: {
      name: '自动化测试-整单5折', type: 'DISCOUNT', scope: 'ORDER', value: 0.5,
      minAmount: 0, totalQty: 0, perUserLimit: 1, status: 1, privateGift: 0,
      startTime: dt(now - 3600000), endTime: dt(now + 86400000)
    }
  })
  const couponId = cRes.data?.id
  ok('商家创建整单5折券', !!couponId, `couponId=${couponId} code=${cRes.code}`)
  if (!couponId) process.exit(1)

  // 2) 学生领券
  const claim = await api(`/coupons/${couponId}/claim`, { method: 'POST', token: sToken })
  const userCouponId = claim.data?.id
  ok('学生领取整单券', !!userCouponId, `userCouponId=${userCouponId} msg=${claim.message || ''}`)
  if (!userCouponId) process.exit(1)

  // 3) 加两道菜并带券结算
  await api('/cart/clear', { method: 'POST', token: sToken })
  await api('/cart/items', { method: 'POST', token: sToken, body: { dishId: 1, quantity: 1 } }) // ¥12
  await api('/cart/items', { method: 'POST', token: sToken, body: { dishId: 3, quantity: 1 } }) // ¥16
  const checkout = await api('/cart/checkout', {
    method: 'POST', token: sToken,
    body: { stallId: 1, pickupTime: dt(now + 7200000), remark: '自动化测试订单', userCouponId }
  })
  const orderId = checkout.data?.[0]?.orderId
  ok('带整单券下单成功', !!orderId, `orderId=${orderId} msg=${checkout.message || ''}`)
  if (!orderId) process.exit(1)

  // 4) 拉订单详情，确认 couponScope 和券后实付
  const myOrders = await api('/orders/my?current=1&size=10', { token: sToken })
  const order = (myOrders.data?.records || []).find(o => o.id === orderId)
  const items = order?.items || []
  const itemsSum = items.reduce((s, i) => s + num(i.amount), 0)
  ok('订单返回 couponScope=ORDER', order?.couponScope === 'ORDER', `couponScope=${order?.couponScope}`)
  ok('整单券后实付 < 菜品原价合计', num(order?.totalAmount) < itemsSum,
    `paid=${order?.totalAmount} itemsSum=${itemsSum.toFixed(2)} discount=${order?.discountAmount}`)
  ok('订单含至少 2 个菜品项', items.length >= 2, `items=${items.length}`)

  // 5) 部分退款必须被拒
  const partial = await api(`/orders/${orderId}/refund`, {
    method: 'POST', token: sToken,
    body: { reason: '自动化测试：部分退款应被拒', itemIds: [items[0].id] }
  })
  ok('整单券订单部分退款被拒绝', partial.code !== 200 && /整单优惠券/.test(partial.message || ''),
    `code=${partial.code} msg=${partial.message}`)

  // 6) 整单退款：金额应等于券后实付
  const full = await api(`/orders/${orderId}/refund`, {
    method: 'POST', token: sToken,
    body: { reason: '自动化测试：整单退款' }
  })
  ok('整单券订单整单退款成功', full.code === 200 && !!full.data?.id, `code=${full.code} msg=${full.message || ''}`)
  ok('整单退款金额 == 券后实付（不按原价多退）',
    full.code === 200 && num(full.data?.amount) === num(order?.totalAmount),
    `refundAmount=${full.data?.amount} paid=${order?.totalAmount}`)

  // 清理：商家驳回这笔退款，避免留下待审核
  if (full.data?.id) {
    await api(`/merchant/refunds/${full.data.id}/reject?remark=${encodeURIComponent('自动化测试清理')}`,
      { method: 'POST', token: mToken })
  }

  // 7) 回归：无券订单部分退款仍可申请
  await api('/cart/clear', { method: 'POST', token: sToken })
  await api('/cart/items', { method: 'POST', token: sToken, body: { dishId: 2, quantity: 1 } })
  const co2 = await api('/cart/checkout', {
    method: 'POST', token: sToken,
    body: { stallId: 1, pickupTime: dt(now + 7200000), remark: '自动化测试无券订单' }
  })
  const order2Id = co2.data?.[0]?.orderId
  const my2 = await api('/orders/my?current=1&size=10', { token: sToken })
  const order2 = (my2.data?.records || []).find(o => o.id === order2Id)
  const item2 = order2?.items?.[0]
  const partial2 = await api(`/orders/${order2Id}/refund`, {
    method: 'POST', token: sToken,
    body: { reason: '自动化测试：无券部分退款', itemIds: item2 ? [item2.id] : [] }
  })
  ok('无券订单部分退款仍可申请（不误伤）', partial2.code === 200 && !!partial2.data?.id,
    `code=${partial2.code} msg=${partial2.message || ''} amount=${partial2.data?.amount}`)
  if (partial2.data?.id) {
    await api(`/merchant/refunds/${partial2.data.id}/reject?remark=${encodeURIComponent('自动化测试清理')}`,
      { method: 'POST', token: mToken })
  }

  console.log('---')
  const pass = results.filter(Boolean).length
  console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e); process.exit(1) })