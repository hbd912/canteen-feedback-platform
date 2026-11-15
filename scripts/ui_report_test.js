const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 950 } })
  const page = await browser.newPage(); page.setDefaultTimeout(60000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await page.evaluate(() => localStorage.clear())
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2200)
    await page.goto(BASE + '/merchant/finance', { waitUntil: 'networkidle0' }); await wait(2200)
    await page.$$eval('.head-actions button', els => els.find(b => b.innerText.includes('AI 经营简报'))?.click())
    await wait(6000)
    const report = await page.evaluate(() => document.querySelector('.ai-report')?.innerText || '')
    ok('AI 经营简报生成', report.length > 40 && /[0-9]/.test(report), report.slice(0, 100))
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/finance-ai-report.png' })
    await page.$$eval('.el-dialog__footer button', els => els.find(b => b.innerText.includes('重新生成'))?.click())
    await wait(6000)
    const report2 = await page.evaluate(() => document.querySelector('.ai-report')?.innerText || '')
    ok('重新生成可用', report2.length > 40, report2.slice(0, 80))
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
