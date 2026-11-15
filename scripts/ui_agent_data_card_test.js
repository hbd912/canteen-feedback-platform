/* 会话式经营 Agent：经营问答聊天里免费要表格/图表 -> 内联渲染数据卡；无关问题纯文字 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const page = await browser.newPage(); page.setDefaultTimeout(90000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  const errors = []
  page.on('pageerror', e => errors.push(e.message))
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await page.evaluate(() => localStorage.clear())
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2200)

    // 进入经营助理
    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2000)
    const menu = await page.evaluate(() => document.querySelector('.el-menu')?.innerText || '')
    ok('侧边栏没有独立销售分析页', !menu.includes('销售分析'))
    const skills = await page.$$eval('.skill-chip', els => els.map(e => e.innerText.replace(/\s+/g, ' ').trim()))
    ok('技能栏无固定销售分析按钮(改为对话内内联)', !skills.some(s => s.includes('销售分析')), skills.join(' | '))

    // 切到经营问答（对话窗口）
    await page.$$eval('.skill-chip', els => els.find(b => b.innerText.includes('经营问答'))?.click()); await wait(800)
    // 输入一条要销售分析的
    await page.click('.assistant-input textarea'); await wait(200)
    await page.type('.assistant-input textarea', '生成近7天销售分析表格')
    await page.click('.send-btn'); await wait(1500)

    // 等待答题 + 内联数据卡出现
    await page.waitForFunction(() => document.querySelectorAll('.agent-data').length >= 1, { timeout: 90000 })
    const nCards = await page.$$eval('.agent-data', els => els.length)
    ok('对话内联出现真实数据卡', nCards >= 1, `data-cards=${nCards}`)
    const nCharts = await page.$$eval('.agent-data canvas', els => els.length)
    ok('数据卡内内联图表渲染', nCharts >= 3, `charts=${nCharts}`)
    const dailyRows = await page.$$eval('.agent-data .el-table tr', els => els.length)
    ok('数据卡内真实表格渲染(≥7行含表头)', dailyRows >= 7, `${dailyRows} 行`)
    const hasDish = await page.evaluate(() => {
      const txt = document.querySelector('.agent-data')?.innerText || ''
      return /宫保鸡丁|鱼香肉丝|水煮肉片|麻婆豆腐|干煸豆角/.test(txt)
    })
    ok('按菜品表含真实菜名', hasDish)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/agent-data-card.png' })

    // 无关问题：不再出现数据卡
    await page.type('.assistant-input textarea', '帮我写一句新菜推荐语')
    await page.click('.send-btn'); await wait(1500)
    await page.waitForFunction(() => {
      const boxes = [...document.querySelectorAll('.msg-bubble .msg-content')]
      return boxes.some(b => b.innerText.includes('推荐'))
    }, { timeout: 30000 })
    const cardAfter = await page.$$eval('.agent-data', els => els.length)
    ok('无关问题不追加数据卡', cardAfter === nCards, `cards=${cardAfter}(此前${nCards})`)

    const jsErr = errors.filter(e => !/favicon|Failed to load|net::/.test(e))
    ok('无 JS 报错', jsErr.length === 0, jsErr.slice(0, 3).join(' | '))
  } catch (e) {
    console.log('FAIL 异常', e.message)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/agent-data-card-err.png' }).catch(()=>{})
    results.push(false)
  } finally {
    await browser.close()
  }
  const pass = results.filter(Boolean).length
  console.log('---')
  console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e.message); process.exit(1) })