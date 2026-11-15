/* 学生端：我的优惠券页 + 结算选券 UI 测试 */
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
  await api('/cart', null, 'DELETE', st.token)
  await api('/cart/items', { dishId: 1, quantity: 2 }, 'POST', st.token)

  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 900 } })
  const page = await browser.newPage(); page.setDefaultTimeout(30000)
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('student01'))?.click()); await wait(300)
  await page.click('.submit-btn')
  await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2200)

  // 1) 我的优惠券页
  await page.goto(BASE + '/my/coupons', { waitUntil: 'networkidle0' }); await wait(2500)
  const couponCards = await page.$$eval('.ct', els => els.length)
  const tabs = await page.$$eval('.ap-pill', els => els.map(e => e.innerText.trim()))
  console.log('券卡片数量:', couponCards, '| tabs:', JSON.stringify(tabs))
  console.log(couponCards > 0 ? 'PASS 我的优惠券页展示券卡片' : 'FAIL 无券卡片')

  // 2) 结算选券
  await page.goto(BASE + '/dishes', { waitUntil: 'networkidle0' }); await wait(3000)
  await page.click('.cart-bar .bar-go'); await wait(1400)
  await page.click('.foot-go'); await wait(2000)
  const shown = await page.$$eval('.cf-coupon:not(.none)', els => els.length)
  const selected = await page.$$eval('.cf-coupon.active', els => els.length)
  const footBefore = await page.evaluate(() => document.querySelector('.dr-foot')?.innerText.replace(/\s+/g, ' '))
  console.log('结算页展示券数:', shown, '| 已选:', selected, '| footer:', footBefore)
  console.log(shown > 0 && shown <= 4 && selected === 1 ? 'PASS 结算页选券与抵扣预览' : 'FAIL 结算选券异常')

  await page.click('.foot-go'); await wait(1200)
  const toast = await page.evaluate(() => Array.from(document.querySelectorAll('.el-message')).map(e => e.innerText).join(' | '))
  console.log('提交结果:', toast)
  console.log(toast.includes('支付成功') ? 'PASS 用券下单成功' : 'FAIL 下单失败')
  await browser.close()
})()
