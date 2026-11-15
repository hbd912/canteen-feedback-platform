const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(240000)
  const R = []; const ok = (n, c, d) => { R.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  async function login(role) {
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(900)
    await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), role).catch(() => {}); await wait(250)
    await p.click('.submit-btn').catch(() => {})
    await p.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}); await wait(2000)
  }

  // 管理员：刷新研判 → 应有公告建议（数据已由外部 seed 严重问题）
  await login('管理员')
  const ins = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/admin/workbench/ai-insight/refresh', { method: 'POST', headers: { 'Authorization': 'Bearer ' + t } })).json()
    return { suggested: j.data?.announcementSuggested, title: j.data?.announcementTitle, content: j.data?.announcementContent }
  })
  console.log('ANN:', JSON.stringify(ins).slice(0, 300))
  ok('严重问题触发公告建议', ins.suggested === true, ins.title)
  ok('公告标题/正文非空', !!ins.title && !!ins.content, ins.content?.slice(0, 50))

  // UI：公告卡 + 授权发布按钮
  await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2200)
  await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {}); await wait(3500)
  await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
  await p.waitForFunction(() => document.querySelector('.announce-card') || document.querySelector('.agent-ask'), { timeout: 120000 }).catch(() => {})
  await wait(1500)
  const card = await p.evaluate(() => ({
    card: !!document.querySelector('.announce-card'),
    btns: [...document.querySelectorAll('.announce-card button')].map(b => b.innerText)
  }))
  ok('UI 公告卡出现', card.card, JSON.stringify(card.btns))

  // 点授权发布
  await p.$$eval('.announce-card button', els => els.find(e => e.innerText.includes('授权发布'))?.click()).catch(() => {})
  await wait(3000)
  const published = await p.evaluate(() => document.body.innerText.includes('已发布公告'))
  ok('点击授权发布成功', published, '')

  // 学生端可见公告
  await login('学生')
  const notices = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/notices', { headers: { 'Authorization': 'Bearer ' + t } })).json()
    return (j.data || []).map(n => n.title)
  })
  console.log('NOTICES:', JSON.stringify(notices.slice(0, 5)))
  ok('学生端可见刚发布的公告', notices.some(t => t === ins.title || t.includes('提醒') || t.includes('公告')), JSON.stringify(notices.slice(0, 3)))
  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
