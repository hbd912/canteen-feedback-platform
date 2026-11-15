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
    hasReply: !!document.querySelector('.detail-reply'),
    summary: [...document.querySelectorAll('.insight-bubble .chat-text')].pop()?.innerText?.slice(0, 120)
  }))
  ok('UI 渲染处理明细表', ui.table && ui.rows >= 2, JSON.stringify({ table: ui.table, rows: ui.rows }))
  ok('UI 显示回复内容', ui.hasReply, '')
  ok('UI 总结详细', !!ui.summary && ui.summary.length > 20, ui.summary)
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/rg5-detail.png' })
  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
