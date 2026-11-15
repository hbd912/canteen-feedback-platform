const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const OUT = 'D:/AI/AI Contest/canteen-feedback/screenshots/'
const wait = ms => new Promise(r => setTimeout(r, ms))
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
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 900 } })
  const page = await browser.newPage()
  // 学生端
  await login(page, 'student01')
  await page.goto(BASE + '/customer-service', { waitUntil: 'networkidle0' }); await wait(3000)
  await page.screenshot({ path: OUT + 'student-cs-grouped.png' })
  await page.click('.cs-group-head'); await wait(2200)
  await page.screenshot({ path: OUT + 'student-cs-expanded.png' })
  // 商家端
  await login(page, '100012')
  await page.goto(BASE + '/merchant/customer-service', { waitUntil: 'networkidle0' }); await wait(3000)
  await page.click('.cs-group-head'); await wait(2200)
  await page.screenshot({ path: OUT + 'merchant-cs-grouped.png' })
  // 发券弹窗
  await page.$$eval('.cs-chat-input button', els => els.find(b => b.innerText.includes('附优惠券'))?.click()); await wait(1000)
  await page.screenshot({ path: OUT + 'merchant-coupon-dialog.png' })
  await page.keyboard.press('Escape'); await wait(600)
  // 客服设置
  await page.$$eval('.cs-chat-tools button', els => els.find(b => b.innerText.includes('客服设置'))?.click()); await wait(1200)
  await page.screenshot({ path: OUT + 'merchant-coupon-rules.png' })
  await browser.close()
  console.log('screenshots done')
})()
