const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(240000)
  const R = []; const ok = (n, c, d) => { R.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await p.evaluate(() => localStorage.clear()); await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(900)
  await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), '管理员').catch(() => {}); await wait(250); await p.click('.submit-btn').catch(() => {})
  await p.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}); await wait(2000)

  // 1) refresh: 数量触发的公告建议（模型研判）
  const ins = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/admin/workbench/ai-insight/refresh', { method: 'POST', headers: { 'Authorization': 'Bearer ' + t } })).json()
    return {
      suggested: j.data?.announcementSuggested,
      title: j.data?.announcementTitle,
      content: (j.data?.announcementContent || '').slice(0, 80),
      summary: (j.data?.summary || '').slice(0, 80)
    }
  })
  console.log('ANN:', JSON.stringify(ins))
  ok('数量触发公告建议(模型研判)', ins.suggested === true, ins.title || '')
  ok('公告文案由模型生成', !!ins.content && !ins.content.startsWith('近期收到同学们关于'), ins.content)

  // 2) 商家留言线程里有 AI 上架申请留言
  const msgs = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/admin/merchant-comm/stalls/1/thread', { headers: { 'Authorization': 'Bearer ' + t } })).json()
    return (j.data || []).map(m => ({ dir: m.direction, role: m.authorRole, title: m.title, content: (m.content || '').slice(0, 40) }))
  })
  console.log('THREAD:', JSON.stringify(msgs.slice(0, 4)))
  ok('商家AI上架申请留言到管理员', msgs.some(m => m.role === 'AI' && (m.title || '').includes('上架申请')), '')

  // 3) 全部处理 → 简洁总结
  const handled = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/admin/workbench/ai-insight/handle-all', { method: 'POST', headers: { 'Authorization': 'Bearer ' + t } })).json()
    return { summary: j.data?.summary }
  })
  console.log('HANDLE SUMMARY:', handled.summary)
  ok('处理完有简洁总结(说明做了什么)', !!handled.summary && (handled.summary.includes('处理') || handled.summary.includes('回复')), handled.summary)

  // 4) 商家留言处理后商家收到通知
  const noti = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/notifications?limit=3', { headers: { 'Authorization': 'Bearer ' + t } })).json()
    return (j.data || []).map(n => ({ type: n.type, content: (n.content || '').slice(0, 60) }))
  })
  console.log('ADMIN NOTI:', JSON.stringify(noti))
  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
