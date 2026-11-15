/* 回归：删除最后一个会话不留幽灵 / 中途切换会话 AI 回复不串桌 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const page = await browser.newPage(); page.setDefaultTimeout(45000)
  const results = []; const ok = (n,c,d)=>{results.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await page.evaluate(() => localStorage.clear())
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(()=>{})
    await wait(2200)
    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2200)

    // ---- A. 删除最后一个会话：应自动新建一个会话并保留，不能出现「侧栏空但聊天活着」----
    await page.evaluate(() => { document.querySelectorAll('.conv-del')[0]?.click() }); await wait(500)
    const afterDel = await page.evaluate(() => ({
      convCount: document.querySelectorAll('.conv-item').length,
      emptyShown: !!document.querySelector('.conv-empty'),
      hasWelcome: [...document.querySelectorAll('.msg-text')].some(e => e.innerText.includes('经营小助手'))
    }))
    ok('删除最后一个会话后自动新建（侧栏非空且无幽灵）',
      afterDel.convCount === 1 && !afterDel.emptyShown && afterDel.hasWelcome,
      JSON.stringify(afterDel))
    // 再删除一次（应仍留 1 个），然后发送一条，刷新后该消息要仍在（说明有真实存储，非幽灵）
    await page.click('textarea'); await page.type('textarea', '帮我写推荐语')
    await page.click('.send-btn'); await wait(500)
    await page.waitForFunction(() => [...document.querySelectorAll('.msg-text')].some(e => e.innerText.includes('推荐')), { timeout: 45000 }).catch(()=>{})
    const stored = await page.evaluate(() => {
      const convs = JSON.parse(localStorage.getItem('merchant_ai_convs_v1') || '[]')
      return { n: convs.length, firstHasReply: convs[0]?.messages?.some(m => m.content && m.content.includes('推荐')) }
    })
    ok('删除末会话后发送的消息能真正落盘（非幽灵）', stored.n >= 1 && stored.firstHasReply, JSON.stringify(stored))
    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(1500)
    const afterReload = await page.$$eval('.conv-item', els => els.length)
    ok('刷新后历史仍保留', afterReload >= 1, `conv=${afterReload}`)

    // ---- B. 中途切换会话：请求进行中切到另一会话，AI 回复应落到原会话 ----
    await page.evaluate(() => { document.querySelectorAll('.new-btn')[0]?.click() }); await wait(400)
    const n0 = await page.$$eval('.conv-item', els => els.length)
    await page.click('textarea'); await page.type('textarea', '这个月卖得怎么样？')
    await page.click('.send-btn'); await wait(200)          // 请求刚发出
    await page.evaluate(() => { document.querySelectorAll('.conv-item')[0]?.click() }); await wait(400)  // 切回第一个
    await page.waitForFunction(() => [...document.querySelectorAll('.msg-text')].some(e => e.innerText.includes('收入') || e.innerText.includes('营收') || e.innerText.includes('订单')), { timeout: 45000 }).catch(()=>{})
    const race = await page.evaluate(() => {
      const convs = JSON.parse(localStorage.getItem('merchant_ai_convs_v1') || '[]')
      // 找到发起请求的那个会话（标题含「这个月」），看它是否存到了带数据/回答的 AI 消息
      const target = convs.find(c => c.messages?.some(m => m.content && m.content.includes('这个月')))
      const hasAiReply = target?.messages?.some(m => m.role === 'assistant' && (m.charts?.length || (m.answer || m.content || '').length > 10))
      return { targetFound: !!target, hasAiReply: !!hasAiReply, msgCount: target?.messages?.length }
    })
    ok('中途切换会话：回复落到原会话并被持久化', race.targetFound && race.hasAiReply, JSON.stringify(race))
  } catch (e) {
    console.log('FAIL 异常', e.message)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/fix-err.png' }).catch(()=>{})
    results.push(false)
  } finally { await browser.close() }
  const pass = results.filter(Boolean).length
  console.log('---'); console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e.message); process.exit(1) })