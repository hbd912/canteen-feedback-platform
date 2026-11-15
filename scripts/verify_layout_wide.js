const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1920, height: 1080 } })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  const R = []; const ok = (n, c, d) => { R.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  async function login(role) {
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(900)
    await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), role).catch(() => {}); await wait(250)
    await p.click('.submit-btn').catch(() => {})
    await p.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}); await wait(2000)
  }

  // 工作台（大屏 1920）
  await login('管理员')
  await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2500)
  await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {}); await wait(2500)
  const wb = await p.evaluate(() => {
    const page = document.querySelector('.wb-page').getBoundingClientRect()
    const chat = document.querySelector('.wb-chat').getBoundingClientRect()
    return { pageW: Math.round(page.width), chatW: Math.round(chat.width), chatH: Math.round(chat.height), winW: window.innerWidth }
  })
  console.log('WORKBENCH:', JSON.stringify(wb))
  ok('工作台大屏加宽(>=1200)', wb.pageW >= 1200, `${wb.pageW}px`)
  ok('对话窗大屏加高(>=750)', wb.chatH >= 750, `${wb.chatH}px`)
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/wide-workbench.png' })

  // 管理员商家沟通线程：回复气泡应为白色
  await p.goto(BASE + '/admin/feedback-center?tab=merchant', { waitUntil: 'networkidle0' }); await wait(2500)
  await p.$$eval('.wmc-stall', els => els[0]?.click()).catch(() => {}); await wait(1500)
  const bubble = await p.evaluate(() => {
    const el = document.querySelector('.wmc-msg.from-admin .wmc-msg-body')
    return el ? { bg: getComputedStyle(el).backgroundColor, color: getComputedStyle(el).color } : null
  })
  console.log('BUBBLE:', JSON.stringify(bubble))
  ok('管理员线程回复气泡为白色', !!bubble && bubble.bg.includes('255, 255, 255'), bubble?.bg || '无回复气泡')
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/wide-admin-thread.png' })

  // 商家留言页（大屏）
  await login('商家')
  await p.goto(BASE + '/merchant/messages', { waitUntil: 'networkidle0' }); await wait(2500)
  const mm = await p.evaluate(() => {
    const page = document.querySelector('.mm-page').getBoundingClientRect()
    return { pageW: Math.round(page.width) }
  })
  console.log('MERCHANT MSG:', JSON.stringify(mm))
  ok('商家留言页大屏加宽(>=1050)', mm.pageW >= 1050, `${mm.pageW}px`)
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/wide-merchant-msg.png' })

  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
