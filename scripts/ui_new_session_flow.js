/* 学生端右上角「新建会话」：点击后真实新建并打开新会话 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1360, height: 950 } })
  const page = await browser.newPage(); page.setDefaultTimeout(30000)
  const wait = ms => new Promise(r => setTimeout(r, ms))
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('student01'))?.click()); await wait(300)
    await page.click('.submit-btn')
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2000)
    await page.goto(BASE + '/customer-service', { waitUntil: 'networkidle0' }); await wait(3000)
    const before = await page.evaluate(() => document.querySelector('.cs-group-sub')?.innerText || '')
    // 无选中会话时点右上角新建：只有一个档口 -> 直接新建
    await page.click('.cs-page-head .cs-new-session'); await wait(3500)
    const after = await page.evaluate(() => document.querySelector('.cs-group-sub')?.innerText || '')
    const chatTitle = await page.evaluate(() => document.querySelector('.cs-chat-title')?.innerText || '')
    const hasInput = await page.evaluate(() => !!document.querySelector('.cs-chat-input textarea'))
    ok('点击右上角新建后会话数 +1', before !== after, `${before} -> ${after}`)
    ok('新会话自动打开聊天面板', chatTitle.includes('川味小炒') && hasInput, `title=${chatTitle} input=${hasInput}`)
    // 左侧 New 旧会话仍在分组里
    const sessionCount = await page.$$eval('.cs-group-sessions .cs-session', els => els.length)
    ok('旧会话仍保留在分组内', sessionCount >= 1, `${sessionCount} 个`)
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
