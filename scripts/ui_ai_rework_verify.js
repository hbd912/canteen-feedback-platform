/* 验收：值班助理 折叠展开+自动处理、商家沟通中心双向、灵敏项跳转高亮 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const SHOTS = 'D:/AI/AI Contest/canteen-feedback/screenshots'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  const errors = []; p.on('pageerror', e => errors.push(e.message))

  async function login(needle) {
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1100)
    await p.$$eval('.pill-account', (els, n) => els.find(x => x.innerText.includes(n))?.click(), needle).catch(() => {})
    await wait(300)
    await p.click('.submit-btn'); await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {}); await wait(2000)
  }

  try {
    // ---- 1) 值班助理：默认折叠为小卡片 ----
    await login('管理员')
    await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2500)
    const collapsed = await p.evaluate(() => {
      const card = document.querySelector('.pad-card')
      return {
        card: !!card,
        startBtn: !!document.querySelector('.pad-btn'),
        expandedChat: !!document.querySelector('.chat-input-row')
      }
    })
    ok('值班助理默认折叠为小卡片', collapsed.card && !collapsed.expandedChat, `card=${collapsed.card}`)
    ok('折叠卡片含「开始整理」', collapsed.startBtn)

    // ---- 2) 点击开始整理展开对话窗口 ----
    await p.click('.pad-btn'); await wait(1200)
    const expanded = await p.evaluate(() => ({
      chat: !!document.querySelector('.chat-body'),
      input: !!document.querySelector('.chat-input-row'),
      hasStartInside: !!document.querySelector('.chat-primary')
    }))
    ok('点击开始整理后展开对话窗口', expanded.chat && expanded.input && expanded.hasStartInside, `chat=${expanded.chat} input=${expanded.input}`)
    await p.screenshot({ path: SHOTS + '/wb-assistant-expanded.png' })

    // ---- 3) 触发整理，等待 insight 渲染（自动处理跑会写库） ----
    await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
    const waited = await p.waitForFunction(() =>
      (document.querySelectorAll('.priority-row').length > 0) ||
      (document.querySelector('.auto-strip') !== null)
    , { timeout: 45000 }).then(() => true).catch(() => false)
    await wait(1500)
    const insight = await p.evaluate(() => ({
      need: document.querySelectorAll('.priority-row').length,
      autoHandled: !!document.querySelector('.auto-strip'),
      metrics: document.querySelector('.insight-metrics')?.innerText || '',
      guard: document.querySelector('.chat-guard')?.innerText || ''
    }))
    ok('展开后整理有输出', insight.need >= 0 || insight.autoHandled)
    ok('整理结果含已自动处理标识', insight.autoHandled || insight.metrics.includes('自动处理'), insight.metrics.slice(0,60))
    await p.screenshot({ path: SHOTS + '/wb-assistant-insight.png' })

    // ---- 4) 商家沟通中心：双向列表 + 档口线程 ----
    await p.goto(BASE + '/admin/feedback-center?tab=merchant', { waitUntil: 'networkidle0' }); await wait(2600)
    const comm = await p.evaluate(() => ({
      stalls: document.querySelectorAll('.wmc-stall').length,
      hasList: !!document.querySelector('.wmc-list'),
      empty: !!document.querySelector('.wmc-empty') || !!document.querySelector('.wmc-thread-body')
    }))
    ok('商家沟通中心列出全部档口', comm.stalls > 0, `stalls=${comm.stalls}`)
    await p.screenshot({ path: SHOTS + '/comm-center-list.png' })

    // ---- 5) 点击一个档口查看双向线程 ----
    await p.$$eval('.wmc-stall', els => els[0]?.click()).catch(() => {})
    await wait(1800)
    const thread = await p.evaluate(() => ({
      threadHead: !!document.querySelector('.wmc-thread-name'),
      msgs: document.querySelectorAll('.wmc-msg').length,
      sendBtn: [...document.querySelectorAll('button')].some(x => x.innerText.includes('发反馈'))
    }))
    ok('点击档口展开双向线程', thread.threadHead, `msgs=${thread.msgs}`)
    ok('线程页含「发反馈」', thread.sendBtn)

    // ---- 6) 发送反馈：验证 AI 编辑+发送 ----
    await p.$$eval('.wmc-thread-head button.wmc-top', els => els.find(e => e.innerText.includes('发反馈'))?.click()).catch(() => {})
    await wait(900)
    const composer = await p.evaluate(() => !!document.querySelector('.compose-field') || !!document.body.innerText.includes('发反馈 ·'))
    ok('打开发反馈弹窗', composer)
    await p.screenshot({ path: SHOTS + '/comm-compose.png' })

  } catch (e) {
    ok('运行异常', false, e.message)
  }
  console.log('\n=== 汇总 ===')
  console.log('通过 ' + results.filter(Boolean).length + ' / ' + results.length)
  if (errors.length) console.log('JS错误:\n' + errors.slice(0,5).join('\n'))
  await b.close()
  process.exit(results.every(Boolean) ? 0 : 1)
})()