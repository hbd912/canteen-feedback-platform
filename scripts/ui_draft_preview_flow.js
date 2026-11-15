/* 草稿预览全流程：AI 生成草稿 -> 默认档口预览可见 -> 管理页草稿条 -> 打回重做 -> 上架 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const API = 'http://localhost:8080/api'
const wait = ms => new Promise(r => setTimeout(r, ms))
async function api(p, payload, method, token) {
  const h = { Authorization: 'Bearer ' + (token || '') }
  let b
  if (payload !== undefined && payload !== null) { h['Content-Type'] = 'application/json'; b = JSON.stringify(payload) }
  return (await fetch(API + p, { method: method || (payload ? 'POST' : 'GET'), headers: h, body: b })).json()
}
;(async () => {
  const mt = (await api('/auth/login', { username: '100012', password: '123456' }, 'POST')).data
  const before = (await api('/dishes?stallId=1&current=1&size=100', null, 'GET', mt.token)).data.total
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 950 } })
  const page = await browser.newPage(); page.setDefaultTimeout(60000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  const errors = []
  page.on('pageerror', e => errors.push(e.message))
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()) })
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await page.evaluate(() => { localStorage.clear() })
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2200)

    // 1) AI 生成草稿（不发布）
    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2200)
    const input = await page.$('.wz-upload input[type=file]')
    await input.uploadFile('D:/AI/AI Contest/canteen-feedback/gourmet food/10036.jpg')
    await wait(2000)
    await page.click('.wz-input .ap-btn--brand')
    await page.waitForSelector('.wz-draft', { timeout: 50000 }); await wait(800)
    const draftName = await page.evaluate(() => document.querySelector('.wz-draft-name')?.innerText || '')
    ok('AI 生成草稿', !!draftName, draftName)
    await page.evaluate(() => {
      const d = JSON.parse(localStorage.getItem('canteen_dish_drafts') || '[]')
      return d.length
    }).then(n => ok('草稿已写入草稿箱', n >= 1, n + ' 条'))

    // 2) 菜品管理默认显示档口预览，且草稿可见
    await page.goto(BASE + '/merchant/dishes', { waitUntil: 'networkidle0' }); await wait(2800)
    const activePill = await page.evaluate(() => document.querySelector('.mode-switch .ap-pill.active')?.innerText || '')
    ok('默认进入档口预览', activePill.includes('档口预览'), activePill)
    const previewText = await page.evaluate(() => document.querySelector('.pv-dishes')?.innerText || '')
    const draftBadges = await page.$$eval('.pv-draft-badge', els => els.length)
    ok('预览里出现草稿卡片', previewText.includes(draftName) && draftBadges >= 1, `badge=${draftBadges}`)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/draft-preview.png' })

    // 3) 切到菜品管理，草稿条 + 操作按钮
    await page.$$eval('.mode-switch .ap-pill', els => els.find(b => b.innerText.includes('菜品管理'))?.click()); await wait(900)
    const stripText = await page.evaluate(() => document.querySelector('.draft-strip')?.innerText.replace(/\s+/g, ' ') || '')
    ok('管理页显示 AI 草稿条', stripText.includes(draftName) && stripText.includes('满意，上架') && stripText.includes('打回重做'), stripText.slice(0, 100))
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/draft-manage.png' })

    // 4) 打回重做：跳到经营助理并恢复草稿
    await page.$$eval('.draft-card .el-button', els => els.find(b => b.innerText.includes('打回重做'))?.click())
    await wait(2000)
    ok('打回跳到经营助理', page.url().includes('ai-assistant'), page.url())
    const hydrated = await page.evaluate(() => document.querySelector('.wz-draft-name')?.innerText || '')
    ok('草稿在对话里恢复', hydrated === draftName, hydrated)
    // 重做一版
    await page.type('.wz-actions .el-input input', '描述短一点，名字更家常')
    await page.$$eval('.wz-btn-row .el-button', els => els.find(b => b.innerText.includes('不满意'))?.click())
    await wait(18000)
    const newName = await page.evaluate(() => {
      const list = Array.from(document.querySelectorAll('.wz-draft-name'))
      return list.length ? list[list.length - 1].innerText : ''
    })
    ok('重做生成新版草稿', !!newName, newName)

    // 5) 回预览：旧草稿被替换
    await page.goto(BASE + '/merchant/dishes', { waitUntil: 'networkidle0' }); await wait(2500)
    const previewText2 = await page.evaluate(() => document.querySelector('.pv-dishes')?.innerText || '')
    const draftCount = await page.$$eval('.pv-draft-badge', els => els.length)
    ok('预览里只保留重做后的草稿', draftCount === 1 && previewText2.includes(newName), `count=${draftCount}, name=${newName}`)

    // 6) 管理页满意上架
    await page.$$eval('.mode-switch .ap-pill', els => els.find(b => b.innerText.includes('菜品管理'))?.click()); await wait(900)
    await page.$$eval('.draft-card .el-button', els => els.find(b => b.innerText.includes('满意'))?.click())
    await wait(2500)
    const after = (await api('/dishes?stallId=1&current=1&size=100', null, 'GET', mt.token)).data.total
    const stripGone = await page.evaluate(() => !document.querySelector('.draft-strip'))
    ok('满意后真正上架', after === before + 1 && stripGone, `菜品 ${before}->${after}，草稿条消失=${stripGone}`)
    ok('页面无 JS 报错', errors.length === 0, errors.slice(0, 2).join(' | '))
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
