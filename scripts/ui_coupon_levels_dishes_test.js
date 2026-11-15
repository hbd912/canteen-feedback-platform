/* UI：优惠券两层（商家 -> 该商家券/整单单份）+ 发券弹窗实时显示新上架菜品 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
const { execSync } = require('child_process')
function sql(q) { return execSync(`mysql -uroot -p123456 canteen_feedback -N -e "${q}"`, { encoding: 'utf-8' }).trim() }
async function login(page, user) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await page.$$eval('.pill-account', (els, u) => els.find(x => x.innerText.includes(u))?.click(), user); await wait(300)
  await page.click('.submit-btn')
  await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2200)
}
;(async () => {
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  sql("DELETE FROM dish WHERE name='维护性测试新菜'")
  sql("INSERT INTO dish (stall_id,name,price,category,status,deleted) VALUES (1,'维护性测试新菜',11.00,'热菜',1,0)")

  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 900 } })
  const page = await browser.newPage(); page.setDefaultTimeout(30000)
  try {
    // 学生：两层优惠券
    await login(page, 'student01')
    await page.goto(BASE + '/my/coupons', { waitUntil: 'networkidle0' }); await wait(2500)
    const merchants = await page.$$eval('.merchant-card', els => els.map(e => e.innerText.replace(/\s+/g, ' ').trim()))
    ok('第一层按商家展示', merchants.length >= 1, JSON.stringify(merchants.slice(0, 2)))
    await page.click('.merchant-card'); await wait(1200)
    const scopes = await page.$$eval('.scope-filter .ap-pill', els => els.map(e => e.innerText.trim()))
    const cards = await page.$$eval('.coupon-grid .ct', els => els.length)
    ok('第二层显示整单/单份菜筛选', scopes.some(s => s.includes('整单')) && scopes.some(s => s.includes('单份菜')), JSON.stringify(scopes))
    ok('第二层显示该商家的券', cards > 0, cards + ' 张')
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/coupons-level2.png' })
    await page.click('.back-btn'); await wait(800)
    ok('可返回商家列表', (await page.$$eval('.merchant-card', els => els.length)) >= 1)

    // 商家：发券弹窗实时显示新菜
    await login(page, '100012')
    await page.goto(BASE + '/merchant/customer-service', { waitUntil: 'networkidle0' }); await wait(3000)
    await page.click('.cs-group-head'); await wait(1600)
    await page.click('.cs-group-sessions .cs-session'); await wait(1600)
    await page.$$eval('.cs-chat-input button', els => els.find(b => b.innerText.includes('附优惠券'))?.click()); await wait(1200)
    await page.$$eval('.el-dialog .ap-pill', els => els.find(b => b.innerText.includes('单份菜'))?.click()); await wait(600)
    await page.click('.el-dialog .el-select'); await wait(1000)
    const options = await page.$$eval('.el-select-dropdown__item', els => els.map(e => e.innerText.trim()))
    ok('发券菜品下拉包含刚刚上架的新菜', options.includes('维护性测试新菜'), JSON.stringify(options.slice(0, 8)))
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/coupon-dish-new.png' })
  } catch (e) { console.log('ERROR', e.message) } finally {
    sql("DELETE FROM dish WHERE name='维护性测试新菜'")
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
