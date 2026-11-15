const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage()
  await p.goto('http://localhost:5173/login', { waitUntil: 'networkidle0' })
  await p.evaluate(() => localStorage.clear())
  await p.goto('http://localhost:5173/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await p.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
  await p.click('.submit-btn'); await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2200)
  await p.goto('http://localhost:5173/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2500)
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/apple-home.png' })
  // switch to sales, send
  await p.$$eval('.skill-chip', els => els.find(e => e.innerText.includes('销售分析'))?.click()); await wait(500)
  await p.type('textarea', '生成近7天销售分析表格')
  await p.click('.send-btn'); await wait(2000)
  try {
    await p.waitForFunction(() => document.querySelectorAll('.agent-data canvas').length >= 3, { timeout: 90000 })
    await wait(1500)
    await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/apple-data.png' })
    console.log('data OK')
  } catch { console.log('data NOT rendered, snapshot below') }
  const body = await p.evaluate(() => document.querySelector('.assistant-body')?.innerText.slice(0, 200) || '')
  console.log('TAIL:', JSON.stringify(body.slice(-150)))
  await b.close()
})().catch(e => { console.error('ERR', e.message); process.exit(1) })