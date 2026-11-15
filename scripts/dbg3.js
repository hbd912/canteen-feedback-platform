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
  // find 发反馈 button by text across all buttons
  const btnInfo = await p.evaluate(() => [...document.querySelectorAll('button')].filter(x => x.innerText.includes('发反馈')).map((x,i) => ({i, t: x.innerText.trim(), cls: x.className, vis: !!(x.offsetWidth||x.offsetHeight)})))
  console.log('发反馈 buttons:', JSON.stringify(btnInfo))
  // click the visible one
  await p.evaluate(() => [...document.querySelectorAll('button')].find(x => x.innerText.includes('发反馈') && (x.offsetWidth||x.offsetHeight))?.click())
  await wait(1000)
  const dlg = await p.evaluate(() => {
    const d = document.querySelector('.el-overlay-dialog, .el-dialog')
    return { found: !!d, html: d ? d.outerHTML.slice(0,600) : null }
  })
  console.log('DIALOG:', JSON.stringify(dlg))
  await b.close()
})()
