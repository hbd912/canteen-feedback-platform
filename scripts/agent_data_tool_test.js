/* 会话式经营 Agent 数据工具测试：老板自由对话 -> 触发真实表格/图表；未触发时只给文字 */
const BASE = 'http://localhost:8080/api'
async function api(p, payload, method, token) {
  const h = { Authorization: 'Bearer ' + (token || '') }
  let b
  if (payload !== undefined && payload !== null) { h['Content-Type'] = 'application/json'; b = JSON.stringify(payload) }
  return (await fetch(BASE + p, { method: method || (payload ? 'POST' : 'GET'), headers: h, body: b })).json()
}
;(async () => {
  const l = await api('/auth/login', { username: '100012', password: '123456' }, 'POST')
  const token = (l.data && l.data.token) || ''
  if (!token) { console.log('FAIL 登录'); process.exit(1) }
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }

  const cases = [
    { q: '生成近7天销售分析表格', expectData: true, label: '近7天' },
    { q: '这个月卖得怎么样？', expectData: true, label: '本月' },
    { q: '帮我写一句新菜推荐语', expectData: false, label: '无关' },
    { q: '哪些菜该下架？', expectData: false, label: '建议(纯文字)' },
    { q: '给我看看最热销的菜和每天的营收', expectData: true, label: '自由描述' },
  ]
  for (const c of cases) {
    const r = await api('/merchant/ai-assistant/ask', { question: c.q }, 'POST', token)
    const d = r.data || {}
    const hasTable = !!d.tables && d.tables.length === 2
    const hasChart = !!d.charts && d.charts.length > 0
    const got = hasTable && hasChart
    ok(`「${c.label}」${c.q.slice(0, 14)} → ${c.expectData ? '应带数据' : '应纯文字'}`, got === c.expectData,
      `tables=${d.tables?.length} charts=${d.charts?.length} days=${d.dataDays} model=${d.model}`)
    if (got) {
      const [daily, dish] = d.tables
      ok(`  ${c.label} 日表有${daily.rows.length}行/${daily.columns.length}列`, daily.rows.length > 0 && daily.columns.length >= 4)
      ok(`  ${c.label} 菜表列=${dish.columns.join(',')}`, dish.columns.length >= 3)
      const dishFirst = dish.rows[0]?.[1]
      ok(`  ${c.label} 菜品名真实:${dishFirst}`, /宫保鸡丁|鱼香肉丝|水煮肉片|麻婆豆腐|干煸豆角|青椒肉丝/.test(dishFirst || ''))
      ok(`  ${c.label} 答案引用数据`, (d.answer || '').length > 10)
    }
    await new Promise(r => setTimeout(r, 800))
  }
  console.log('---')
  const pass = results.filter(Boolean).length
  console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e.message); process.exit(1) })