const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 900 } })
  const page = await browser.newPage()
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
  await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2200)
  await page.goto(BASE + '/merchant/home', { waitUntil: 'networkidle0' }); await wait(3000)
  await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/merchant-home-v2.png' })
  await browser.close(); console.log('done')
})()
