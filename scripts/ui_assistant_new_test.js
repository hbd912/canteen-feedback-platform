/* 经营助理新功能 UI 测试：历史侧栏 / 新建对话 / 输入区小助手 emoji / 无emoji技能胶囊 / 图5图表不漂移 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const page = await browser.newPage(); page.setDefaultTimeout(60000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  const errors = []; page.on('pageerror', e => errors.push(e.message))
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await page.evaluate(() => localStorage.clear())
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2200)

    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2200)

    // 1. 历史侧栏存在，且初始至少 1 个对话
    const convCount = await page.$$eval('.conv-item', els => els.length)
    ok('历史侧栏渲染出对话存档', convCount >= 1, `conv=${convCount}`)

    // 2. 新建对话：点"新建对话"后标题/消息重置、侧栏 +1
    const before = await page.$$eval('.conv-item', els => els.length)
    await page.$$eval('.new-btn', els => els[0]?.click()); await wait(600)
    const after = await page.$$eval('.conv-item', els => els.length)
    const msgs = await page.$$eval('.msg-text', els => els.length)
    ok('新建对话后侧栏 +1 且消息只剩欢迎语', after === before + 1 && msgs === 1, `before=${before} after=${after} msgs=${msgs}`)

    // 3. 输入区上方小助手 emoji 人格条（图4风格）
    const stripAvatar = await page.$('.assistant-strip .strip-avatar')
    const headAvatar = await page.$('.head-avatar')
    ok('输入区上方小助手 emoji 条 + 头部小助手头像存在', !!(stripAvatar && headAvatar))

    // 4. 功能胶囊在输入区（图2），纯文字无 emoji；点选后小助手右侧提示词随功能变化（图3）
    const chipsInDock = await page.$$eval('.input-dock .skill-chip', els => els.length)
    ok('功能胶囊在输入框区域（图2）', chipsInDock === 3, `chips=${chipsInDock}`)
    const pillTexts = await page.$$eval('.skill-chip', els => els.map(e => e.innerText))
    ok('功能胶囊纯文字无 emoji', pillTexts.every(t => !/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u.test(t)), pillTexts.join(' | '))
    await page.$$eval('.skill-chip', els => els.find(b => b.innerText.includes('销售分析'))?.click()); await wait(400)
    const hintSales = await page.$eval('.assistant-strip .strip-text', e => e.innerText)
    await page.$$eval('.skill-chip', els => els.find(b => b.innerText.includes('上传菜品'))?.click()); await wait(400)
    const hintUpload = await page.$eval('.assistant-strip .strip-text', e => e.innerText)
    ok('点选功能后小助手右侧提示词切换（图3）',
      hintSales.includes('销售分析') && hintUpload.includes('上传菜品'),
      `salesHint=${hintSales.slice(0,18)} uploadHint=${hintUpload.slice(0,18)}`)
    // 回到经营问答，避免影响后续步骤
    await page.$$eval('.skill-chip', els => els.find(b => b.innerText.includes('经营问答'))?.click()); await wait(300)

    // 5. 收藏/删除历史对话
    const delBtns = await page.$$eval('.conv-item .conv-del', els => els.length)
    ok('每条历史有删除按钮（hover 显示）', delBtns >= 1, `del=${delBtns}`)

    // 6. 历史侧栏可收起/展开
    await page.$$eval('.hide-btn', els => els[0]?.click()); await wait(400)
    const collapsed = await page.$eval('.conv-side', el => el.className.includes('collapsed'))
    await page.$$eval('.hide-btn', els => els[0]?.click()); await wait(400)
    const restored = await page.$eval('.conv-side', el => !el.className.includes('collapsed'))
    ok('历史侧栏可收起与展开', collapsed && restored, `collapsed=${collapsed} restored=${restored}`)

    // 7. 切到销售分析，发一句 → 数据卡图表正常渲染且不漂移（图5修复：等号前 EChart 用 ResizeObserver）
    await page.$$eval('.skill-chip', els => els.find(b => b.innerText.includes('销售分析'))?.click()); await wait(600)
    await page.click('textarea'); await page.type('textarea', '生成近7天销售分析表格')
    await page.click('.send-btn'); await wait(1500)
    await page.waitForFunction(() => document.querySelectorAll('.agent-data canvas').length >= 3, { timeout: 30000 })
    // 验证图表横向不越界：canvas 的 rect 左边缘 >= 卡片左边缘 - 1
    const overflows = await page.evaluate(() => {
      const card = document.querySelector('.assistant-card')?.getBoundingClientRect()
      const bad = []
      document.querySelectorAll('.ad-chart').forEach((el, i) => {
        const r = el.getBoundingClientRect()
        if (card && (r.left < card.left - 2 || r.right > card.right + 2)) bad.push(i)
      })
      return { bad, cardLeft: card?.left, cardRight: card?.right }
    })
    ok('图5修复：图表在卡片内不越界漂移', overflows.bad.length === 0, JSON.stringify(overflows))

    const jsErr = errors.filter(e => !/favicon|net::|ResizeObserver|Failed to load resource|two instances/.test(e))
    ok('无 JS 报错', jsErr.length === 0, jsErr.slice(0,3).join(' | '))
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/assistant-new.png' })
  } catch (e) {
    console.log('FAIL 异常', e.message)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/assistant-new-err.png' }).catch(()=>{})
    results.push(false)
  } finally { await browser.close() }
  const pass = results.filter(Boolean).length
  console.log('---'); console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e.message); process.exit(1) })