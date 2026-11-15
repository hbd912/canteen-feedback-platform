/* 经营助理「销售分析」技能：选中 -> 让 AI 分析 -> 图表/表格 -> 下载 -> 切区间 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
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

    // 侧边栏：不再有经营分析/独立销售分析页；菜品管理在经营助理前
    const menu = await page.evaluate(() => document.querySelector('.el-menu')?.innerText || '')
    ok('侧边栏没有经营分析', !menu.includes('经营分析'))
    ok('侧边栏没有独立销售分析页', !menu.includes('销售分析'))
    const dishesIdx = menu.indexOf('菜品管理'), assistantIdx = menu.indexOf('经营助理')
    ok('菜品管理在经营助理之前', dishesIdx > 0 && assistantIdx > dishesIdx, `菜品管理@${dishesIdx} 经营助理@${assistantIdx}`)

    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2000)
    const skills = await page.$$eval('.skill-chip', els => els.map(e => e.innerText.replace(/\s+/g, ' ').trim()))
    const salesChip = skills.find(s => s.includes('销售分析'))
    ok('技能栏里的销售分析已启用', !!salesChip && !salesChip.includes('即将上线'), salesChip)

    // 选中销售分析，不点不加载
    await page.$$eval('.skill-chip', els => els.find(b => b.innerText.includes('销售分析'))?.click())
    await wait(800)
    const emptyText = await page.evaluate(() => document.querySelector('.sales-empty')?.innerText.replace(/\s+/g, ' ') || '')
    ok('未点分析前显示空状态', emptyText.includes('让 AI 看看'), emptyText.slice(0, 60))
    ok('未点分析前不显示图表', (await page.$$eval('.chart-card', els => els.length)) === 0)

    // 让 AI 分析
    await page.$$eval('.sales-panel .head-actions button', els => els.find(b => b.innerText.includes('让 AI 分析'))?.click())
    await page.waitForSelector('.summary-card', { timeout: 90000 }); await wait(1400)
    const summary = await page.evaluate(() => document.querySelector('.summary-text')?.innerText || '')
    ok('AI 分析结果出现', summary.length > 20, summary.slice(0, 80))
    const chartCount = await page.$$eval('.chart-card', els => els.length)
    const canvasCount = await page.$$eval('.chart-card canvas', els => els.length)
    ok('统计图渲染', chartCount >= 3 && canvasCount >= 3, `charts=${chartCount} canvas=${canvasCount}`)
    const rows = await page.$$eval('.sales-table .el-table__body tr', els => els.length)
    ok('真实数据表格（7 行）', rows === 7, `${rows} 行`)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/sales-skill.png' })

    // 按菜品 + 下载
    await page.$$eval('.table-actions .ap-pill', els => els.find(b => b.innerText.includes('按菜品'))?.click()); await wait(500)
    const dishFirst = await page.evaluate(() => document.querySelector('.sales-table .el-table__body tr')?.innerText.replace(/\s+/g, ' ') || '')
    ok('按菜品表格是真实菜品', /宫保鸡丁|鱼香肉丝|水煮肉片|麻婆豆腐|干煸豆角/.test(dishFirst), dishFirst.slice(0, 50))
    await page.evaluate(() => {
      window.__downloads = []
      const orig = HTMLAnchorElement.prototype.click
      HTMLAnchorElement.prototype.click = function () { if (this.download) window.__downloads.push(this.download); else orig.call(this) }
    })
    await page.$$eval('.table-actions button', els => els.find(b => b.innerText.includes('下载 CSV'))?.click()); await wait(400)
    await page.$$eval('.chart-download', els => els[0]?.click()); await wait(400)
    const downloads = await page.evaluate(() => window.__downloads || [])
    ok('CSV 与图表 PNG 可下载', downloads.some(n => n.endsWith('.csv')) && downloads.some(n => n.endsWith('.png')), JSON.stringify(downloads))

    // 切 30 天：旧结果收起，点分析后 30 行
    await page.$$eval('.sales-panel .head-actions .ap-pill', els => els.find(b => b.innerText.includes('30'))?.click()); await wait(600)
    const backEmpty = await page.evaluate(() => !!document.querySelector('.sales-empty'))
    ok('切换区间后等待再次分析', backEmpty)
    await page.$$eval('.sales-panel .head-actions button', els => els.find(b => b.innerText.includes('让 AI 分析'))?.click())
    await page.waitForSelector('.summary-card', { timeout: 90000 }); await wait(1200)
    const rows30 = await page.$$eval('.sales-table .el-table__body tr', els => els.length)
    ok('近 30 天分析后表格 30 行', rows30 === 30, `${rows30} 行`)
    ok('页面无 JS 报错', errors.length === 0, errors.slice(0, 2).join(' | '))
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
