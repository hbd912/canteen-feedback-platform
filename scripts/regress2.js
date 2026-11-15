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

  await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2500)
  await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {}); await wait(3500)

  // 1. 输入框固定：记录 chat-body 高度与 dock 位置
  const layout1 = await p.evaluate(() => {
    const body = document.querySelector('.chat-body')
    const dock = document.querySelector('.chat-dock')
    return { bodyH: body?.clientHeight, dockBottom: dock?.getBoundingClientRect().bottom, winH: window.innerHeight }
  })
  ok('对话窗固定高度(输入框不随消息下坠)', layout1.bodyH > 100 && layout1.dockBottom <= 1100, JSON.stringify(layout1))

  // 2. 开始整理 → 公告建议卡
  await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
  await p.waitForFunction(() => document.querySelector('.agent-ask') || document.querySelector('.announce-card'), { timeout: 90000 }).catch(() => {})
  await wait(1500)
  const announce = await p.evaluate(() => ({
    suggested: !!document.querySelector('.announce-card'),
    title: document.querySelector('.announce-title')?.innerText,
    hasAuth: [...document.querySelectorAll('.announce-card button')].some(x => x.innerText.includes('授权发布')),
    hasDecline: [...document.querySelectorAll('.announce-card button')].some(x => x.innerText.includes('暂不发布'))
  }))
  ok('严重卫生问题出现公告建议 + 授权/不发布按钮', announce.suggested && announce.hasAuth && announce.hasDecline, JSON.stringify(announce))
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/rg2-announce.png' })

  // 3. 历史会话：新建 → 历史面板可见旧会话
  await p.$$eval('.badge-new', els => els[0]?.click()).catch(() => {})
  await wait(800)
  await p.$$eval('.badge-hist', els => els[0]?.click()).catch(() => {})
  await wait(500)
  const hist = await p.evaluate(() => ({
    panel: !!document.querySelector('.hist-panel'),
    items: [...document.querySelectorAll('.hist-item')].map(x => x.innerText.replace(/\s+/g, ' ').slice(0, 30))
  }))
  ok('历史会话面板可查看旧会话', hist.panel && hist.items.length >= 2, JSON.stringify(hist.items.slice(0, 3)))
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/rg2-history.png' })

  // 4. 全部处理 → 菜品审核通知管理员 + 商家警告
  await p.evaluate(() => { document.querySelector('.hist-close')?.click() })
  await wait(300)
  await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
  await p.waitForFunction(() => document.querySelector('.agent-ask'), { timeout: 90000 }).catch(() => {})
  await wait(1200)
  await p.$$eval('.agent-btn', els => els.find(e => e.innerText.includes('全部处理'))?.click()).catch(() => {})
  await wait(20000)
  const noti = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/notifications?limit=10', { headers: { 'Authorization': 'Bearer ' + t } })).json()
    return (j.data || []).map(n => ({ type: n.type, content: (n.content || '').slice(0, 70) }))
  })
  console.log('ADMIN NOTIFICATIONS:', JSON.stringify(noti))
  ok('菜品AI审核结果通知管理员', noti.some(n => n.content.includes('AI 已审核菜品')), '')
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/rg2-final.png' })
  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
