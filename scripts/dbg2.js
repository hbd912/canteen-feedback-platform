const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  p.on('response', r => { if (r.status() >= 400) console.log('HTTP', r.status(), r.url().slice(0,80)) })
  // login admin
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await p.evaluate(() => localStorage.clear())
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1000)
  await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), '管理员').catch(() => {})
  await wait(250); await p.click('.submit-btn').catch(() => {})
  await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {}); await wait(2000)

  await p.goto(BASE + '/admin/feedback-center?tab=merchant', { waitUntil: 'networkidle0' }); await wait(3000)
  console.log('URL:', p.url())
  console.log('BODY:', (await p.evaluate(() => document.body.innerText.replace(/\s+/g,' '))).slice(0,200))
  const wmc = await p.evaluate(() => ({ stalls: document.querySelectorAll('.wmc-stall').length, top: document.querySelectorAll('.wmc-top').length }))
  console.log('WMC:', JSON.stringify(wmc))
  // click first stall
  await p.$$eval('.wmc-stall', els => els[0]?.click()).catch(() => {})
  await wait(1500)
  console.log('after stall click, .wmc-top:', await p.evaluate(() => document.querySelectorAll('.wmc-top').length))
  await p.$$eval('.wmc-top', els => els[0]?.click()).catch(() => {})
  await wait(1000)
  console.log('dialog body text:', await p.evaluate(() => (document.querySelector('.el-dialog')?.innerText || 'NO DIALOG').replace(/\s+/g,' ').slice(0,150)))
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/dbg-merchant.png' })
  await b.close()
})()
