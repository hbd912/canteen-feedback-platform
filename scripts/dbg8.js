const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  // merchant 商家 login pill
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await p.evaluate(() => localStorage.clear())
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(900)
  await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), '商家').catch(() => {})
  await wait(250); await p.click('.submit-btn').catch(() => {})
  await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {}); await wait(2000)
  console.log('merchant url after login:', p.url())
  await p.goto(BASE + '/merchant/messages', { waitUntil: 'networkidle0' }); await wait(2800)
  const info = await p.evaluate(() => ({
    url: location.href,
    has: document.body.innerText.includes('验收测试通知'),
    text: document.body.innerText.replace(/\s+/g,' ').slice(0,400)
  }))
  console.log('MERCHANT MESSAGES:', JSON.stringify(info, null, 2))
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/merchant-inbox.png' })
  await b.close()
})()
