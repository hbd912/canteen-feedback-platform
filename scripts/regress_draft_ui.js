const puppeteer = require('puppeteer-core')
const fs = require('fs')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(240000)
  const R = []; const ok = (n, c, d) => { R.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await p.evaluate(() => localStorage.clear()); await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(900)
  await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), '商家').catch(() => {}); await wait(250); await p.click('.submit-btn').catch(() => {})
  await p.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}); await wait(2000)
  await p.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2500)
  // 选择「上传菜品」技能
  await p.$$eval('button', els => els.find(x => x.innerText.trim() === '上传菜品')?.click()).catch(() => {})
  await wait(400)
  // 上传图片
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAPUlEQVR4nO3BAQ0AAADCoPdPbQ43oAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAfg0mAAABY29tAAAAAElFTkSuQmCC', 'base64')
  fs.writeFileSync('D:/AI/AI Contest/canteen-feedback/scripts/t.png', png)
  const inputs = await p.$$('input[type=file]')
  console.log('file inputs:', inputs.length)
  if (inputs.length) await inputs[inputs.length - 1].uploadFile('D:/AI/AI Contest/canteen-feedback/scripts/t.png')
  await wait(4000)
  // 点击发送按钮（空文本+图时 Enter 不触发，必须点按钮）
  await p.click('.send-btn').catch(() => {})
  await wait(35000)
  const mid = await p.evaluate(() => ({
    draftCards: document.querySelectorAll('.wz-draft,.draft-card').length,
    failMsg: document.body.innerText.includes('这次请求失败了'),
    ls: (localStorage.getItem('canteen_dish_drafts') || '').slice(0, 100)
  }))
  console.log('AFTER SEND:', JSON.stringify(mid))
  ok('经营助理生成草稿卡', mid.draftCards > 0, JSON.stringify(mid))
  ok('草稿已写入草稿箱(localStorage)', mid.ls.length > 0, mid.ls.slice(0, 60))
  ok('未出现"这次请求失败了"', !mid.failMsg)
  await p.goto(BASE + '/merchant/dishes', { waitUntil: 'networkidle0' }); await wait(3000)
  const pv = await p.evaluate(() => ({ badges: [...document.querySelectorAll('.pv-draft-badge')].map(x => x.innerText) }))
  ok('档口预览显示草稿徽标', pv.badges.includes('草稿'), JSON.stringify(pv.badges))
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/rg-draft-preview.png' })
  await b.close()
  console.log('\n=== 通过 ' + R.filter(Boolean).length + ' / ' + R.length + ' ===')
})()
