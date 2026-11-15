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
  await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2500)
  await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {})
  await wait(900)
  await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
  await p.waitForFunction(() => document.querySelectorAll('.priority-row').length > 0, { timeout: 60000 }).catch(()=>console.log('NO_PRIORITY'))
  await wait(1500)
  await p.$$eval('.priority-row', els => els[0]?.click()).catch(()=>console.log('CLICK_FAIL'))
  await wait(5000)
  const info = {
    url: p.url(),
    flash: await p.evaluate(() => !!document.querySelector('.flash-target')),
    items: await p.evaluate(() => document.querySelectorAll('.feedback-item').length),
    id44: await p.evaluate(() => !!document.querySelector('.feedback-item[data-recid="44"]')),
    bodyHas44: await p.evaluate(() => document.body.innerText.includes('44'))
  }
  console.log('RESULT:', JSON.stringify(info))
  await b.close()
})()
