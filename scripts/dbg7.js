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
  // find the compose overlay (has 发反馈 in its text)
  const marker = '验收测试通知'
  const filled = await p.evaluate((marker) => {
    const overlays = [...document.querySelectorAll('.el-overlay')]
    const compose = overlays.find(o => (o.innerText||'').includes('发反馈'))
    if (!compose) return { err: 'no compose overlay' }
    const input = compose.querySelector('input')
    const textarea = compose.querySelector('textarea')
    if (!input || !textarea) return { err: 'no input/textarea', input:!!input, textarea:!!textarea }
    input.focus(); input.value = marker; input.dispatchEvent(new Event('input', {bubbles:true}))
    textarea.focus(); textarea.value = '内容要点：请留意整改'; textarea.dispatchEvent(new Event('input', {bubbles:true}))
    return { ok: true }
  }, marker)
  console.log('FILL:', JSON.stringify(filled))
  await wait(400)
  // click 发送通知
  const clicked = await p.evaluate(() => {
    const overlays = [...document.querySelectorAll('.el-overlay')]
    const compose = overlays.find(o => (o.innerText||'').includes('发送通知'))
    if (!compose) return 'no send btn'
    const btn = [...compose.querySelectorAll('button')].find(x => x.innerText.includes('发送通知'))
    if (!btn) return 'btn not found'
    btn.click(); return 'clicked'
  })
  console.log('SEND:', clicked)
  await wait(2500)
  const thread = await p.evaluate((marker) => {
    const t = document.querySelector('.wmc-thread-body')?.innerText || ''
    const has = t.includes(marker)
    // read thread HTML to confirm direction/author bubble
    return { has, snippet: t.split('\n').filter(x=>x.includes(marker)).join(' | ') }
  })
  console.log('THREAD:', JSON.stringify(thread))
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/dbg-sent.png' })
  await b.close()
})()
