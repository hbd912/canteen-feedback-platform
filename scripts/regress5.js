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

  // 1) 全部处理 → summary 详细 + handledDetails
  const handled = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const j = await (await fetch('/api/admin/workbench/ai-insight/handle-all', { method: 'POST', headers: { 'Authorization': 'Bearer ' + t } })).json()
    return { summary: j.data?.summary, details: (j.data?.handledDetails || []).map(d => ({ type: d.sourceLabel, title: d.title, action: d.action, reply: d.reply, count: d.count })) }
  })
  console.log('SUMMARY:', handled.summary)
  console.log('DETAILS:', JSON.stringify(handled.details, null, 1).slice(0, 900))
  ok('总结详细说明做了什么', !!handled.summary && handled.summary.length > 20, handled.summary?.slice(0, 80))
  ok('返回处理明细列表', handled.details.length > 0, `${handled.details.length} 条`)
  ok('明细含"怎么回的"(reply)', handled.details.some(d => d.reply), handled.details.find(d => d.reply)?.reply?.slice(0, 40))
  ok('明细含菜品审核动作', handled.details.some(d => d.type === '菜品审核' && d.action), '')

  // 2) UI 渲染明细表
  await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2200)
  await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {}); await wait(3500)
  await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
  await p.waitForFunction(() => document.querySelector('.agent-ask'), { timeout: 120000 }).catch(() => {})
  await wait(1200)
  await p.$$eval('.agent-btn', els => els.find(e => e.innerText.includes('全部处理'))?.click()).catch(() => {})
  await p.waitForFunction(() => document.querySelectorAll('.insight-bubble').length >= 2, { timeout: 200000 }).catch(() => {})
  await wait(2500)
  const ui = await p.evaluate(() => ({
    table: !!document.querySelector('.detail-table'),
    rows: document.querySelectorAll('.detail-row').length,
    hasReply: !!document.querySelector('.detail-reply')
  }))
  ok('UI 渲染处理明细表', ui.table && ui.rows >= 2, JSON.stringify(ui))
  ok('UI 显示回复内容', ui.hasReply, '')
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/rg5-detail.png' })

  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
