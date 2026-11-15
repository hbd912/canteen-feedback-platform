/* AI 销售分析 UI：图表渲染、切换区间、表格、下载、导航顺序 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 950 } })
  const page = await browser.newPage(); page.setDefaultTimeout(90000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  const errors = []
  page.on('pageerror', e => errors.push(e.message))
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()) })
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await page.evaluate(() => localStorage.clear())
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2200)

    // 导航顺序与旧页面
    const menu = await page.evaluate(() => document.querySelector('.el-menu')?.innerText || '')
    const dishesIdx = menu.indexOf('菜品管理'), assistantIdx = menu.indexOf('经营助理')
    ok('菜品管理在经营助理之前', dishesIdx > 0 && assistantIdx > dishesIdx, `菜品管理@${dishesIdx} 经营助理@${assistantIdx}`)
    ok('经营分析已移除', !menu.includes('经营分析'))
    ok('销售分析入口存在', menu.includes('销售分析'))

    // 页面
    await page.goto(BASE + '/merchant/sales-analysis', { waitUntil: 'networkidle0' })
    await page.waitForSelector('.summary-card', { timeout: 90000 }); await wait(1500)
    const summary = await page.evaluate(() => document.querySelector('.summary-text')?.innerText || '')
    ok('AI 总结显示', summary.length > 20, summary.slice(0, 80))
    const chartCount = await page.$$eval('.chart-card', els => els.length)
    const canvasCount = await page.$$eval('.chart-card canvas', els => els.length)
    ok('图表渲染（≥3 个且 canvas 正常）', chartCount >= 3 && canvasCount >= 3, `charts=${chartCount} canvas=${canvasCount}`)
    const insightCount = await page.$$eval('.insight-list li', els => els.length)
    ok('AI 发现点显示', insightCount >= 2, `${insightCount} 条`)
    const rowCount = await page.$$eval('.sales-table .el-table__body tr', els => els.length)
    ok('按日真实表格有数据', rowCount === 7, `${rowCount} 行`)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/sales-analysis.png' })

    // 切按菜品
    await page.$$eval('.table-actions .ap-pill', els => els.find(b => b.innerText.includes('按菜品'))?.click()); await wait(600)
    const dishFirst = await page.evaluate(() => document.querySelector('.sales-table .el-table__body tr')?.innerText.replace(/\s+/g, ' ') || '')
    ok('按菜品表格显示真实菜品', /鱼香肉丝|宫保鸡丁|水煮肉片|麻婆豆腐|干煸豆角/.test(dishFirst), dishFirst.slice(0, 60))

    // 下载：拦截 a.click 记录 download 名称
    await page.evaluate(() => {
      window.__downloads = []
      const orig = HTMLAnchorElement.prototype.click
      HTMLAnchorElement.prototype.click = function () {
        if (this.download) window.__downloads.push(this.download)
        else orig.call(this)
      }
    })
    await page.$$eval('.table-actions button', els => els.find(b => b.innerText.includes('下载 CSV'))?.click()); await wait(500)
    await page.$$eval('.chart-download', els => els[0]?.click()); await wait(500)
    const downloads = await page.evaluate(() => window.__downloads || [])
    ok('CSV 与图表图片可下载', downloads.some(n => n.endsWith('.csv')) && downloads.some(n => n.endsWith('.png')), JSON.stringify(downloads))

    // 切换 30 天
    await page.$$eval('.head-actions .ap-pill', els => els.find(b => b.innerText.includes('30'))?.click())
    await page.waitForSelector('.summary-card', { timeout: 90000 }); await wait(1200)
    const rows30 = await page.$$eval('.sales-table .el-table__body tr', els => els.length)
    ok('切换近 30 天重新分析', rows30 === 30, `${rows30} 行`)
    ok('页面无 JS 报错', errors.length === 0, errors.slice(0, 2).join(' | '))
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
