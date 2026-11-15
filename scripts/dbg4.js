const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await p.evaluate(() => localStorage.clear())
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1000)
  await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), '管理员').catch(() => {})
  await wait(250); await p.click('.submit-btn').catch(() => {})
  await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {}); await wait(2000)
  await p.goto(BASE + '/admin/feedback-center?tab=merchant', { waitUntil: 'networkidle0' }); await wait(2600)
  await p.$$eval('.wmc-stall', els => els[0]?.click()).catch(() => {}); await wait(1500)
  await p.evaluate(() => [...document.querySelectorAll('button')].find(x => x.innerText.includes('发反馈') && (x.offsetWidth||x.offsetHeight))?.click())
  await wait(1200)
  const all = await p.evaluate(() => [...document.querySelectorAll('.el-overlay')].map(o => ({
    aria: o.getAttribute('aria-label'),
    hasInput: !!o.querySelector('input'),
    hasTextarea: !!o.querySelector('textarea'),
    text: (o.innerText||'').replace(/\s+/g,' ').slice(0,80)
  })))
  console.log('OVERLAYS:', JSON.stringify(all, null, 2))
  await b.close()
})()
