/* UI：客服券卡片领取 + 商家发单菜券 + 商家个人中心对账 */
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
async function login(page, user) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await page.$$eval('.pill-account', (els, u) => els.find(x => x.innerText.includes(u))?.click(), user); await wait(300)
  await page.click('.submit-btn')
  await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2200)
}
;(async () => {
  const st = (await api('/auth/login', { username: 'student01', password: '123456' }, 'POST')).data
  const mt = (await api('/auth/login', { username: '100012', password: '123456' }, 'POST')).data
  // 确保有一张待领取券
  const ss = (await api('/customer-service/sessions', null, 'GET', st.token)).data || []
  const sid = ss[0].id
  await api(`/customer-service/sessions/${sid}/grant-coupon`, { rate: 0.9, scope: 'ORDER' }, 'POST', mt.token)
  const fin = (await api('/merchant/finance/summary', null, 'GET', mt.token)).data

  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 900 } })
  const page = await browser.newPage(); page.setDefaultTimeout(30000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }

  try {
    // 学生：打开有券卡片的会话并领取
    await login(page, 'student01')
    await page.goto(BASE + '/customer-service', { waitUntil: 'networkidle0' }); await wait(3000)
    await page.click('.cs-group-head'); await wait(1800)
    const clicked = await page.$$eval('.cs-group-sessions .cs-session', els => {
      const e = els.find(x => (x.innerText || '').includes('券卡片领取'))
      if (e) { e.click(); return true }
      return false
    })
    await wait(2200)
    ok('找到带券卡片的会话', clicked)
    const cardText = await page.evaluate(() => document.querySelector('.chat-coupon')?.innerText.replace(/\s+/g, ' ') || '')
    ok('聊天里显示可领取券卡片', cardText.includes('领取'), cardText.slice(0, 80))
    await page.click('.chat-coupon .cc-claim'); await wait(2200)
    const status = await page.evaluate(() => document.querySelector('.chat-coupon .cc-status')?.innerText || '')
    ok('点击领取后变已领取', status.includes('已领取'), status)

    // 学生券包页分组
    await page.goto(BASE + '/my/coupons', { waitUntil: 'networkidle0' }); await wait(2200)
    const merchants = await page.$$eval('.merchant-card', els => els.map(e => e.innerText.replace(/\s+/g, ' ').trim()))
    ok('券第一层按商家展示', merchants.some(t => t.includes('川味小炒')), JSON.stringify(merchants.slice(0, 2)))
    await page.click('.merchant-card'); await wait(1200)
    const scopeTag = await page.evaluate(() => document.querySelector('.ct-scope')?.innerText || '')
    ok('券卡片展示适用范围', !!scopeTag, scopeTag)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/student-coupons-v2.png' })

    // 商家：发券弹窗有整单/单份菜
    await login(page, '100012')
    await page.goto(BASE + '/merchant/customer-service', { waitUntil: 'networkidle0' }); await wait(3000)
    await page.click('.cs-group-head'); await wait(1800)
    await page.click('.cs-group-sessions .cs-session'); await wait(1800)
    await page.$$eval('.cs-chat-input button', els => els.find(b => b.innerText.includes('附优惠券'))?.click()); await wait(900)
    const dialogText = await page.evaluate(() => document.querySelector('.el-dialog')?.innerText || '')
    ok('发券弹窗有「整单/单份菜」', dialogText.includes('整单') && dialogText.includes('单份菜'))
    await page.$$eval('.el-dialog .ap-pill', els => els.find(b => b.innerText.includes('单份菜'))?.click()); await wait(600)
    const hasDishSelect = await page.evaluate(() => !!document.querySelector('.el-dialog .el-select'))
    ok('选择单份菜后出现菜品选择', hasDishSelect)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/merchant-coupon-scope.png' })
    await page.keyboard.press('Escape'); await wait(600)

    // 商家个人中心对账
    await page.goto(BASE + '/profile', { waitUntil: 'networkidle0' }); await wait(2500)
    const bizText = await page.evaluate(() => document.querySelector('.biz-card')?.innerText.replace(/\s+/g, ' ') || '')
    const bizNums = await page.$$eval('.biz-stat b', els => els.map(e => e.innerText.trim()))
    console.log('经营概览:', bizText.slice(0, 120), '| nums:', JSON.stringify(bizNums))
    ok('商家个人中心显示经营概览', bizText.includes('累计收益') && bizText.includes('可提现余额'))
    ok('个人中心累计收益与财务中心一致',
      bizNums[0] && bizNums[0].replace(/[¥,]/g, '') === Number(fin.totalIncome).toFixed(2),
      `${bizNums[0]} vs ¥${Number(fin.totalIncome).toFixed(2)}`)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/merchant-profile.png' })

    // 档口菜品卡加号
    await page.goto(BASE + '/stalls/1', { waitUntil: 'networkidle0' }); await wait(2500)
    const addInside = await page.evaluate(() => {
      const btn = document.querySelector('.dish-add')
      const card = document.querySelector('.dish-item')
      if (!btn || !card) return null
      const b = btn.getBoundingClientRect(); const c = card.getBoundingClientRect()
      return { inside: b.bottom <= c.bottom + 1 && b.right <= c.right + 1, bottomGap: c.bottom - b.bottom }
    })
    ok('菜品卡加号在卡片内（不再穿模）', addInside && addInside.inside, JSON.stringify(addInside))
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/stall-dish-cards.png' })
  } catch (e) {
    console.log('ERROR', e.message)
  } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
