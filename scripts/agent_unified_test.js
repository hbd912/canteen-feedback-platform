/* 统一对话 Agent 测试：上传技能→草稿卡；销售→数据卡；问答→文字 */
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

  // 上传技能：纯文字描述 → 应返回 draft 草稿卡（不带图）
  const r1 = await api('/merchant/ai-assistant/ask',
    { question: '帮我上架：番茄牛腩，微辣，分量足', skill: 'upload' }, 'POST', token)
  const d1 = r1.data || {}
  ok('上传技能-纯文字返回草稿', !!d1.draft, `detected=${d1.detectedSkill} draft=${JSON.stringify(d1.draft && (({name,price,category})=>({name,price,category}))(d1.draft))}`)
  if (d1.draft) {
    ok('  草稿有菜名', !!d1.draft.name, d1.draft.name)
    ok('  草稿有建议价', d1.draft.priceSuggested === true || Number(d1.draft.price) > 0, `price=${d1.draft.price} suggested=${d1.draft.priceSuggested}`)
    ok('  草稿有描述', (d1.draft.description||'').length > 5)
  }

  // 经营问答（chat 技能）：要销售表格 → 应返回数据卡
  const r2 = await api('/merchant/ai-assistant/ask',
    { question: '生成近7天销售分析表格', skill: 'chat' }, 'POST', token)
  const d2 = r2.data || {}
  ok('经营问答-要表格返回数据卡', d2.tables?.length === 2 && d2.charts?.length > 0,
    `charts=${d2.charts?.length} tables=${d2.tables?.length} days=${d2.dataDays}`)

  // 销售技能（显式）：本月营收 → 数据卡（注意它默认带空客服问题，需保证数据触发）
  const r3 = await api('/merchant/ai-assistant/ask',
    { question: '本月营收和客户数', skill: 'sales' }, 'POST', token)
  const d3 = r3.data || {}
  ok('销售技能-本月营收返回数据卡', d3.tables?.length === 2 && d3.dataDays === 30,
    `charts=${d3.charts?.length} days=${d3.dataDays}`)

  // 经营问答：纯建议 → 纯文字无数据卡
  const r4 = await api('/merchant/ai-assistant/ask',
    { question: '帮我写一句新菜推荐语', skill: 'chat' }, 'POST', token)
  const d4 = r4.data || {}
  ok('经营问答-推荐语纯文字', !d4.draft && !d4.tables && !d4.charts && (d4.answer||'').length > 5,
    `answer=${(d4.answer||'').slice(0,30)}`)

  console.log('---')
  const pass = results.filter(Boolean).length
  console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e.message); process.exit(1) })