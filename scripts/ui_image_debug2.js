const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 950 } })
  const page = await browser.newPage(); page.setDefaultTimeout(60000)
  page.on('console', m => { if (m.type() !== 'debug') console.log('CONSOLE', m.type(), m.text()) })
  page.on('pageerror', e => console.log('PAGEERROR', e.message))
  page.on('requestfailed', r => { if (!r.url().startsWith('blob:')) console.log('REQFAIL', r.url(), r.failure()?.errorText) })
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
  await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2200)
  await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2500)
  const input = await page.$('.wz-upload input[type=file]')
  await input.uploadFile('D:/AI/AI Contest/canteen-feedback/.tmp-test/big_dish.png')
  for (let i = 0; i < 20; i++) {
    await wait(700)
    const st = await page.evaluate(() => ({
      pending: !!document.querySelector('.wz-pending-img'),
      toast: Array.from(document.querySelectorAll('.el-message')).map(e => e.innerText).join(' | '),
      uploading: document.querySelector('.wz-img-btn')?.innerText || ''
    }))
    if (st.pending || st.toast) { console.log('t=%dms' % (i*700), JSON.stringify(st)); if (st.pending) break }
  }
  await browser.close()
})()
