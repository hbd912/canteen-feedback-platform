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

  // 1) 工作台待办统计
  const wb = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/admin/workbench', { headers: { 'Authorization': 'Bearer ' + t } })).json()
    return { todos: j.data.todos.map(x => x.title + ':' + x.count) }
  })
  console.log('BEFORE:', JSON.stringify(wb.todos))

  // 2) 值班助理：展开 -> 欢迎语 -> 开始整理 -> 全部处理
  await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2200)
  await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {}); await wait(3500)
  const welcome = await p.evaluate(() => document.querySelector('.chat-text')?.innerText || '')
  ok('欢迎语为模型生成', !!welcome && !welcome.includes('只有食品安全、卫生、退款这类敏感问题才列出来'), welcome.slice(0, 50))
  await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
  await p.waitForFunction(() => document.querySelector('.agent-ask'), { timeout: 90000 }).catch(() => {})
  await wait(1200)
  const cls = await p.evaluate(() => ({
    need: document.querySelector('.split-num.need')?.innerText,
    auto: document.querySelector('.split-num.auto')?.innerText,
    summary: document.querySelector('.insight-bubble .chat-text')?.innerText?.slice(0, 80)
  }))
  ok('分类汇报（需手动/可自动）', cls.need !== undefined && cls.auto !== undefined, JSON.stringify(cls))
  await p.$$eval('.agent-btn', els => els.find(e => e.innerText.includes('全部处理'))?.click()).catch(() => {})
  await p.waitForFunction(() => document.querySelectorAll('.insight-bubble').length >= 2, { timeout: 200000 }).catch(() => {})
  await wait(2500)

  // 3) 处理后台待办归零 + 退款保持 PENDING
  const after = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/admin/workbench', { headers: { 'Authorization': 'Bearer ' + t } })).json()
    return { todos: j.data.todos.map(x => x.title + ':' + x.count) }
  })
  console.log('AFTER:', JSON.stringify(after.todos))
  ok('处理后台待办仅剩退款(如有)', !after.todos.some(x => x.includes('留言') || x.includes('评价') || x.includes('菜品')), JSON.stringify(after.todos))
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/rg-final.png' })

  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
