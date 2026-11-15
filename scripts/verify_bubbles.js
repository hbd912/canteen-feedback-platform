const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  const R = []; const ok = (n, c, d) => { R.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await p.evaluate(() => localStorage.clear())
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(900)
  await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), '商家').catch(() => {}); await wait(250)
  await p.click('.submit-btn').catch(() => {})
  await p.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}); await wait(2000)
  await p.goto(BASE + '/merchant/messages', { waitUntil: 'networkidle0' }); await wait(2500)

  const r = await p.evaluate(() => {
    const admin = document.querySelector('.mm-item.from-admin')
    const cs = admin ? getComputedStyle(admin) : null
    return {
      unreadTags: document.querySelectorAll('.mm-item .el-tag').length,
      unreadHeader: !!document.querySelector('.mm-unread'),
      adminBg: cs ? cs.backgroundColor : '',
      adminBorder: cs ? cs.borderColor : '',
      who: [...document.querySelectorAll('.mm-who')].map(x => x.innerText),
      bodyColor: admin ? getComputedStyle(admin.querySelector('.mm-msg-body')).color : ''
    }
  })
  console.log('VIEW:', JSON.stringify(r))
  ok('未读标签已移除', r.unreadTags === 0 && !r.unreadHeader, '')
  ok('管理员气泡改白色(非黑)', r.adminBg.includes('255, 255, 255'), r.adminBg)
  ok('正文为深色文字', r.bodyColor.includes('29, 29, 31') || r.bodyColor.includes('0, 0, 0'), r.bodyColor)
  ok('官方名字显示', r.who.includes('小众点评官方'), JSON.stringify(r.who))
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/bubbles-white.png' })
  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
