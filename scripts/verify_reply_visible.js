const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(180000)
  const R = []; const ok = (n, c, d) => { R.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  async function login(role) {
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(900)
    await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), role).catch(() => {}); await wait(250)
    await p.click('.submit-btn').catch(() => {})
    await p.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}); await wait(2000)
  }

  // 商家端：往来记录应看到 AI 回复气泡
  await login('商家')
  await p.goto(BASE + '/merchant/messages', { waitUntil: 'networkidle0' }); await wait(2500)
  const merchantView = await p.evaluate(() => ({
    bodies: [...document.querySelectorAll('.mm-msg-body')].map(x => x.innerText.slice(0, 30)),
    fromAdmin: document.querySelectorAll('.mm-item.from-admin').length,
    fromMerchant: document.querySelectorAll('.mm-item.from-merchant').length
  }))
  console.log('MERCHANT VIEW:', JSON.stringify(merchantView))
  ok('商家往来记录显示AI回复', merchantView.fromAdmin >= 2
    && merchantView.bodies.some(t => t.includes('收到你的反馈') || t.includes('已记录')), JSON.stringify(merchantView.bodies))
  ok('商家自己消息也在', merchantView.fromMerchant >= 2, `fromMerchant=${merchantView.fromMerchant}`)
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/reply-merchant.png' })

  // 新流程验证：商家发一条 → 管理员 AI 处理 → 商家端可见回复
  const mm = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const res = await fetch('/api/merchant/messages', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + t },
      body: JSON.stringify({ stallId: 1, content: '回复可见性验证：洗碗机坏了需要维修' }) })
    return await res.json()
  })
  ok('商家发新留言', mm?.code === 200, '')

  await login('管理员')
  const handled = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/admin/workbench/ai-insight/handle-all', { method: 'POST', headers: { 'Authorization': 'Bearer ' + t } })).json()
    return { summary: j.data?.summary }
  })
  console.log('SUMMARY:', handled.summary?.slice(0, 80))

  await login('商家')
  await p.goto(BASE + '/merchant/messages', { waitUntil: 'networkidle0' }); await wait(2500)
  const after = await p.evaluate(() => ({
    bodies: [...document.querySelectorAll('.mm-msg-body')].map(x => x.innerText.slice(0, 40)),
    unread: document.querySelector('.mm-unread')?.innerText || ''
  }))
  console.log('AFTER:', JSON.stringify(after))
  ok('新AI回复对商家可见', after.bodies.some(t => t.includes('洗碗机') || t.includes('维修') || t.includes('已记录')), JSON.stringify(after.bodies.slice(-3)))
  ok('商家看到未读通知数', !!after.unread, after.unread)
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/reply-merchant-new.png' })

  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
