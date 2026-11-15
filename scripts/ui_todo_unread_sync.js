/* 商家待办页：铃铛「全部已读」后「客服消息」待办卡应同步刷新 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const API = 'http://localhost:8080/api'
async function api(p, payload, method, token) {
  const h = { Authorization: 'Bearer ' + (token || '') }
  let b
  if (payload !== undefined && payload !== null) { h['Content-Type'] = 'application/json'; b = JSON.stringify(payload) }
  return (await fetch(API + p, { method: method || (payload ? 'POST' : 'GET'), headers: h, body: b })).json()
}
;(async () => {
  const stu = (await api('/auth/login', { username: 'student01', password: '123456' }, 'POST')).data
  const ns = (await api('/customer-service/sessions', { stallId: 1, newSession: true }, 'POST', stu.token)).data
  await api(`/customer-service/sessions/${ns.id}/messages`, { content: '老板，还有饭吗？' }, 'POST', stu.token)
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1360, height: 950 } })
  const page = await browser.newPage(); page.setDefaultTimeout(30000)
  const wait = ms => new Promise(r => setTimeout(r, ms))
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn')
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2000)
    await page.goto(BASE + '/merchant/todos', { waitUntil: 'networkidle0' }); await wait(3000)
    const before = await page.evaluate(() => document.body.innerText.match(/有 (\d+) 条客服消息待回复/)?.[1] || '0')
    console.log('待办卡初始客服消息数:', before)
    await page.click('.notice-btn'); await wait(1000)
    await page.$$eval('.notice-panel button', els => els.find(b => b.innerText.includes('全部已读'))?.click())
    await wait(2500)
    const after = await page.evaluate(() => document.body.innerText.match(/有 (\d+) 条客服消息待回复/)?.[1] || '0')
    ok('铃铛全部已读后待办卡同步清零', Number(after) === 0, `${before} -> ${after}`)
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
