/* 验证2：灵敏项跳转高亮 + 商家沟通中心发消息落库（正确驱动 Element Plus 弹窗） */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const SHOTS = 'D:/AI/AI Contest/canteen-feedback/screenshots'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  const errors = []; p.on('pageerror', e => errors.push(e.message.slice(0,120)))
  async function login(role) {
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1000)
    await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), role).catch(() => {})
    await wait(250); await p.click('.submit-btn').catch(() => {})
    await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {}); await wait(2000)
  }
  try {
    // ---- A) 灵敏项跳转高亮 ----
    await login('管理员')
    await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2200)
    await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {})
    await wait(900)
    await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
    await p.waitForFunction(() => document.querySelectorAll('.priority-row').length > 0, { timeout: 45000 }).catch(() => {})
    await wait(1500)
    const needCount = await p.evaluate(() => document.querySelectorAll('.priority-row').length)
    ok('感知到需人工的敏感项', needCount > 0, `need=${needCount}`)
    // 记录点击后应跳转的 id（从 click 无法读 targetUrl，改读目标页 URL 是否带 id）
    await p.$$eval('.priority-row', els => els[0]?.click()).catch(() => {})
    // 目标页异步加载后定位高亮
    await wait(3000)
    const flashRes = await p.evaluate(() => {
      const el = document.querySelector('.flash-target')
      return el ? { found: true, text: (el.innerText || '').slice(0, 40) } : { found: false }
    })
    ok('目标页定位强调 flash-target', flashRes.found, flashRes.found ? flashRes.text : '未定位到（检查分页/时序）')
    await p.screenshot({ path: SHOTS + '/flash-highlight.png' })

    // ---- B) 商家沟通中心：发通知落库 ----
    await p.goto(BASE + '/admin/feedback-center?tab=merchant', { waitUntil: 'networkidle0' }); await wait(2400)
    await p.$$eval('.wmc-stall', els => els[0]?.click()).catch(() => {}); await wait(1600)
    // 打开发反馈弹窗
    await p.$$eval('.wmc-top', els => els[0]?.click()).catch(() => {})
    await wait(900)
    const dialogOpen = await p.evaluate(() => !!document.querySelector('.el-dialog input, .el-dialog textarea'))
    ok('打开发反馈弹窗', dialogOpen)
    // 填主题 + 内容要点（Element Plus 输入）
    const topicInput = await p.$('.el-dialog input.el-input__inner, .el-dialog .compose-field input')
    if (topicInput) { await topicInput.click(); await topicInput.type('验收测试通知') }
    const contentTextarea = await p.$('.el-dialog textarea.el-textarea__inner, .el-dialog .el-textarea textarea')
    if (contentTextarea) { await contentTextarea.click(); await contentTextarea.type('这是端到端验收发送的通知内容，请商家留意。') }
    await wait(300)
    // 点击「发送通知」
    await p.$$eval('.el-dialog button', els => els.find(e => e.innerText.includes('发送通知'))?.click()).catch(() => {})
    await wait(2500)
    const newMsg = await p.evaluate(() => [...document.querySelectorAll('.wmc-msg')].map(m => m.innerText).find(t => t.includes('验收测试通知')))
    ok('发通知后线程显示新消息', !!newMsg, newMsg ? newMsg.slice(0, 50) : '未在线程中显示')
    await p.screenshot({ path: SHOTS + '/comm-thread-after-send.png' })

    // ---- C) 商家端看到管理员通知 ----
    await login('商家')
    await p.goto(BASE + '/merchant/messages', { waitUntil: 'networkidle0' }); await wait(2400)
    const merchantSees = await p.evaluate(() => {
      const t = document.body.innerText
      return { hasNotice: t.includes('验收测试通知'), hasInbox: t.includes('往来记录') }
    })
    ok('商家端能看到管理员发出的通知', merchantSees.hasNotice, `notice=${merchantSees.hasNotice}`)
    await p.screenshot({ path: SHOTS + '/merchant-inbox.png' })
  } catch (e) {
    ok('运行异常', false, e.message)
  }
  console.log('\n=== 汇总 ===')
  console.log('通过 ' + results.filter(Boolean).length + ' / ' + results.length)
  if (errors.length) console.log('JS错误:\n' + errors.slice(0,4).join('\n'))
  await b.close()
  process.exit(results.every(Boolean) ? 0 : 1)
})()