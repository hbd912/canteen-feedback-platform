/* 统一对话 Agent UI：单窗口 + 技能切换 + 内联草稿卡 + 内联数据卡 */
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

    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2000)

    // 1. 单窗口：同时含技能栏 + 输入框（不再有分页面板）
    const hasSkillBar = await page.$$eval('.skill-chip', els => els.length) >= 3
    const hasInput = !!(await page.$('textarea'))
    const hasDraftPanel = !!(await page.$('.wz-panel'))
    const hasSalesPanel = !!(await page.$('.sales-panel'))
    ok('单窗口统一（技能栏+输入框都在，无独立上传/销售面板）', hasSkillBar && hasInput && !hasDraftPanel && !hasSalesPanel)

    // 2. 技能切换：纯文字胶囊（无 emoji），切换不往聊天插消息
    const pillTexts = await page.$$eval('.skill-chip', els => els.map(e => e.innerText))
    ok('技能胶囊为纯文字无 emoji', pillTexts.every(t => !/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u.test(t)), pillTexts.join(' | '))
    await page.$$eval('.skill-chip', els => els.find(b => b.innerText.includes('上传菜品'))?.click()); await wait(600)
    const uploadPlaceholder = await page.$eval('textarea', e => e.placeholder || '')
    ok('上传技能切换占位提示', uploadPlaceholder.includes('上架草稿'), uploadPlaceholder.slice(0,30))
    const msgsAfterSwitch = await page.$$eval('.msg-text', els => els.length)
    ok('切换技能不新增消息', msgsAfterSwitch === 1, `msg=${msgsAfterSwitch}（应保持1条问候）`)
    // 输入区固定在卡片底部（不随滚动消失）
    const dockVisible = await page.evaluate(() => {
      const dock = document.querySelector('.input-dock'); if (!dock) return false
      const r = dock.getBoundingClientRect(); const winH = window.innerHeight
      return r.bottom <= winH && r.bottom > winH * 0.5
    })
    ok('输入坞固定在底部可见', dockVisible)

    // 3. 上传技能：纯文字上架 → 草稿卡内联
    await page.click('textarea'); await page.type('textarea', '帮我上架：番茄牛腩，微辣，分量足')
    await page.click('.send-btn'); await wait(1200)
    await page.waitForFunction(() => document.querySelectorAll('.draft-card').length === 1, { timeout: 60000 })
    const draftName = await page.$eval('.draft-card .dc-name', e => e.innerText)
    ok('上传草稿卡内联渲染', !!draftName, draftName)
    ok('草稿卡有价格输入', (await page.$$eval('.draft-card .el-input-number', els => els.length)) === 1)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/agent-unified-draft.png' })

    // 4. 切到销售分析：直接说 → 数据卡内联
    await page.$$eval('.skill-chip', els => els.find(b => b.innerText.includes('销售分析'))?.click()); await wait(600)
    await page.click('textarea'); await page.type('textarea', '生成近7天销售分析表格')
    await page.click('.send-btn'); await wait(1500)
    await page.waitForFunction(() => document.querySelectorAll('.agent-data').length >= 1, { timeout: 90000 })
    const nCharts = await page.$$eval('.agent-data canvas', els => els.length)
    ok('销售分析数据卡内联（图表渲染）', nCharts >= 3, `charts=${nCharts}`)
    const nRows = await page.$$eval('.agent-data .el-table tr', els => els.length)
    ok('数据卡真实表格渲染', nRows >= 7, `rows=${nRows}`)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/agent-unified-data.png' })

    // 5. 同一会话继续聊（上下文保留）
    await page.click('textarea'); await page.type('textarea', '哪些菜该下架？')
    await page.click('.send-btn'); await wait(1500)
    await page.waitForFunction(() => {
      const t = [...document.querySelectorAll('.msg-ai .msg-text')].map(e => e.innerText).join(' ')
      return t.includes('下架') && t.length > 20
    }, { timeout: 60000 })
    const drafts = await page.$$eval('.draft-card', els => els.length)
    const cards = await page.$$eval('.agent-data', els => els.length)
    ok('同会话内草稿卡与数据卡共存（上下文延续）', drafts === 1 && cards === 1, `drafts=${drafts} cards=${cards}`)

    const jsErr = errors.filter(e => !/favicon|net::|Failed to load resource/.test(e))
    ok('无 JS 报错', jsErr.length === 0, jsErr.slice(0,3).join(' | '))
  } catch (e) {
    console.log('FAIL 异常', e.message)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/agent-unified-err.png' }).catch(()=>{})
    results.push(false)
  } finally {
    await browser.close()
  }
  const pass = results.filter(Boolean).length
  console.log('---')
  console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e.message); process.exit(1) })