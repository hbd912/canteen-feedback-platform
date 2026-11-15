const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
async function login(page, user) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await page.$$eval('.pill-account', (els, u) => els.find(x => x.innerText.includes(u))?.click(), user); await wait(300)
  await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2200)
}
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 950 } })
  const page = await browser.newPage(); page.setDefaultTimeout(60000)
  await login(page, '100012')
  // 菜品管理 - 预览
  await page.goto(BASE + '/merchant/dishes', { waitUntil: 'networkidle0' }); await wait(2400)
  await page.$$eval('.mode-switch .ap-pill', els => els.find(b => b.innerText.includes('档口预览'))?.click())
  await wait(2500)
  await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/dishes-preview.png' })
  // 经营助理 - 上传技能 + 草稿
  await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2200)
  const input = await page.$('.wz-upload input[type=file]')
  await input.uploadFile('D:/AI/AI Contest/canteen-feedback/gourmet food/10036.jpg')
  await wait(2000)
  await page.click('.wz-input .ap-btn--brand')
  await page.waitForSelector('.wz-draft', { timeout: 50000 }); await wait(1000)
  await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/assistant-upload-panel.png' })
  await browser.close(); console.log('done')
})()
