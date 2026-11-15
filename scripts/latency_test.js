/* 测经营问答/销售/上传的握手延迟：重点看是否有请求卡满或过慢 */
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
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }

  const cases = [
    { q: '这个月卖得怎么样？', skill: 'chat' },
    { q: '帮我写推荐语', skill: 'chat' },
    { q: '帮我上架：西红柿炒蛋，家常味', skill: 'upload' }
  ]
  for (let round = 0; round < 2; round++) {
    for (const c of cases) {
      const t0 = Date.now()
      const r = await api('/merchant/ai-assistant/ask', { question: c.q, skill: c.skill }, 'POST', token)
      const ms = Date.now() - t0
      const d = r.data || {}
      const okResp = !r.code || r.code === 200 || r.code === 0
      const hasData = d.draft || d.charts?.length || d.answer
      ok(`[${round}] ${c.q.slice(0,12)}`, okResp && hasData && ms < 12000,
        `${ms}ms model=${d.model||''} draft=${!!d.draft} charts=${d.charts?.length||0} code=${r.code||200}`)
    }
  }
  console.log('---')
  const pass = results.filter(Boolean).length
  console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e.message); process.exit(1) })