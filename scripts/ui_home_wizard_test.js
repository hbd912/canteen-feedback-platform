/* 商家首页 + AI 传图上架对话流 + AI 经营托管已删除 */
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

  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 900 } })
  const page = await browser.newPage(); page.setDefaultTimeout(40000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await page.evaluate(() => localStorage.clear())
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2200)

    // 侧边栏不应再有「AI 经营托管」
    const menuText = await page.evaluate(() => document.querySelector('.el-menu')?.innerText || document.body.innerText)
    ok('侧边栏已移除「AI 经营托管」', !menuText.includes('AI 经营托管'))
    // 直接访问旧路由应被重定向
    await page.goto(BASE + '/merchant/ai-ops', { waitUntil: 'networkidle0' }); await wait(1500)
    ok('旧 /merchant/ai-ops 已失效(重定向)', page.url().includes('ai-ops') === false, page.url())

    // 商家首页
    await page.goto(BASE + '/merchant/home', { waitUntil: 'networkidle0' }); await wait(3000)
    ok('档口主页渲染档口信息', await page.evaluate(() => document.body.innerText.includes('档口主页') || !!document.querySelector('.home-hero')))
    const heroText = await page.evaluate(() => document.querySelector('.home-hero')?.innerText.replace(/\s+/g, ' ') || '')
    ok('首页有经营快照(可提现/今日收益)', heroText.includes('可提现余额') && heroText.includes('今日收益'))

    // AI 传图上架
    await page.click('.ap-btn--brand'); await wait(800)
    await page.type('.wz-input .el-input input', '辣子鸡，麻辣香脆，下饭')
    await page.click('.wz-input .el-button--primary'); await wait(18000)
    const draftCard = await page.evaluate(() => document.querySelector('.wz-draft')?.innerText.replace(/\s+/g, ' ') || '')
    ok('AI 生成草稿卡片', draftCard.length > 0, draftCard.slice(0, 60))
    ok('草稿有价格', /[0-9]+\.[0-9]+/.test(draftCard), draftCard.match(/售价[\s\d.]*/)?.[0] || '')
    const priceVal = await page.evaluate(() => document.querySelector('.wz-draft-price-row .el-input-number input')?.value || '')
    ok('售价可编辑且有值', Number(priceVal) > 0, priceVal)

    // 不满意重做
    const beforeDrafts = await page.$$eval('.wz-draft', els => els.length)
    await page.type('.wz-actions .el-input input', '名字再香一点，偏重口')
    await page.$$eval('.wz-btn-row .el-button', els => els.find(b => b.innerText.includes('不满意'))?.click())
    await wait(18000)
    const afterDrafts = await page.$$eval('.wz-draft', els => els.length)
    ok('不满意后生成新版草稿', afterDrafts > beforeDrafts, `${beforeDrafts} -> ${afterDrafts}`)

    // 满意上架
    await page.$$eval('.wz-btn-row .el-button', els => els.find(b => b.innerText.includes('满意'))?.click())
    await wait(3000)
    const uploaded = await page.$$eval('.wz-uploaded', els => els.length)
    ok('满意后显示已上架', uploaded > 0)
    const after = (await api('/dishes?stallId=1&current=1&size=100', null, 'GET', mt.token)).data.total
    ok('菜品数 +1', after === before + 1, `${before} -> ${after}`)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/ai-dish-wizard.png' })
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/merchant-home.png', fullPage: false })
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
