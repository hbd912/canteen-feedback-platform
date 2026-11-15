const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const API = 'http://localhost:8080/api'
const OUT = 'D:/AI/AI Contest/canteen-feedback/screenshots/'
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
  const cart = (await api('/cart', null, 'GET', st.token)).data
  console.log('cart totalQuantity=', cart.totalQuantity)
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 900 } })
  const page = await browser.newPage()
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('student01'))?.click()); await wait(300)
  await page.click('.submit-btn')
  await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2500)
  await page.goto(BASE + '/dishes', { waitUntil: 'networkidle0' }); await wait(3000)
  console.log('cart-bar exists:', !!(await page.$('.cart-bar')))
  await page.click('.cart-bar .bar-go').catch(e => console.log('bar click err', e.message))
  await wait(1600)
  console.log('foot-go exists:', !!(await page.$('.foot-go')))
  await page.click('.foot-go').catch(e => console.log('foot click err', e.message))
  await wait(2000)
  console.log('confirm text:', await page.evaluate(() => (document.querySelector('.dr-body.confirm')?.innerText || '').slice(0, 120)))
  await page.screenshot({ path: OUT + 'checkout-coupon.png' })
  await browser.close()
  console.log('done')
})()
