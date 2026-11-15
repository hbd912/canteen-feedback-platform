const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const API = 'http://localhost:8080/api'
const wait = ms => new Promise(r => setTimeout(r, ms))
async function api(p, payload, method, token) {
  const h = { Authorization: 'Bearer ' + (token || '') }
  let b
  if (payload !== undefined && payload !== null) { h['Content-Type'] = 'application/json'; b = JSON.stringify(payload) }
  return (await fetch(API + p, { method: method || (payload ? 'POST' : 'GET'), headers: h, body: b })).json()
}
;(async () => {
  const st = (await api('/auth/login', { username: 'student01', password: '123456' }, 'POST')).data
  const mt = (await api('/auth/login', { username: '100012', password: '123456' }, 'POST')).data
  const ss = (await api('/customer-service/sessions', null, 'GET', st.token)).data || []
  const sid = ss[0].id
  await api(`/customer-service/sessions/${sid}/grant-coupon`, { rate: 0.85, scope: 'ITEM', dishId: 2 }, 'POST', mt.token)

  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 900 } })
  const page = await browser.newPage()
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('student01'))?.click()); await wait(300)
  await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2200)
  await page.goto(BASE + '/customer-service', { waitUntil: 'networkidle0' }); await wait(2800)
  await page.click('.cs-group-head'); await wait(1600)
  await page.$$eval('.cs-group-sessions .cs-session', els => {
    const e = els.find(x => (x.innerText || '').includes('券卡片领取'))
    if (e) e.click()
  })
  await wait(2200)
  // 滚到最新一条券卡片
  const card = await page.$('.chat-coupon')
  if (card) await card.evaluate(el => el.scrollIntoView({ block: 'center' }))
  await wait(500)
  await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/chat-coupon-card.png' })
  await browser.close()
  console.log('done')
})()
