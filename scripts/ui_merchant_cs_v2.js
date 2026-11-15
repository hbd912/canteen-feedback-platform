/* 商家端客服 UI 走查 v2：按学生分组、删除、客服设置里的发券规则、手动附优惠券、附菜单。
 * 前置：后端 8080、前端 5180。用法: node scripts/ui_merchant_cs_v2.js
 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const API = 'http://localhost:8080/api'

async function api(path, payload, method, token) {
  const headers = { Authorization: 'Bearer ' + (token || '') }
  let body
  if (payload !== undefined && payload !== null) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(payload)
  }
  const res = await fetch(API + path, { method: method || (payload ? 'POST' : 'GET'), headers, body })
  return res.json()
}

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: 'new',
    args: ['--no-sandbox'], defaultViewport: { width: 1360, height: 950 }
  })
  const page = await browser.newPage()
  page.setDefaultTimeout(30000)
  const wait = ms => new Promise(r => setTimeout(r, ms))
  const results = []
  const ok = (name, cond, detail) => { results.push([name, !!cond]); console.log((cond ? 'PASS ' : 'FAIL ') + name + (detail ? '  ' + detail : '')) }

  try {
    // 造一条商家未读：学生给商家发一条消息（新会话，保证可见）
    const stu = (await api('/auth/login', { username: 'student01', password: '123456' }, 'POST')).data
    const ns = (await api('/customer-service/sessions', { stallId: 1, newSession: true }, 'POST', stu.token)).data
    if (ns && ns.id) {
      await api(`/customer-service/sessions/${ns.id}/messages`,
        { content: '老板，今天中午有糖醋里脊吗？' }, 'POST', stu.token)
    }

    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click())
    await wait(300)
    await page.click('.submit-btn')
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2500)
    await page.goto(BASE + '/merchant/customer-service', { waitUntil: 'networkidle0' })
    await wait(3000)

    // 1) 第一层按学生分组
    const groups = await page.$$eval('.cs-group-head', els => els.map(e => e.innerText.replace(/\s+/g, ' ').trim()))
    ok('商家会话按学生分组', groups.length >= 1, JSON.stringify(groups.slice(0, 3)))
    ok('分组头显示学生名', groups[0] && groups[0].includes('魏欣怡'), groups[0] || '')

    // 2) 商家铃铛未读 -> 全部已读
    const bellBefore = await page.evaluate(() => {
      const b = document.querySelector('.notice-btn .el-badge__content')
      return b ? b.textContent : ''
    })
    ok('商家铃铛出现未读', !!bellBefore, `badge=${bellBefore}`)
    await page.click('.notice-btn')
    await wait(1000)
    const hasReadAll = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.notice-panel button')).some(b => b.innerText.includes('全部已读')))
    if (hasReadAll) {
      await page.$$eval('.notice-panel button', els => els.find(b => b.innerText.includes('全部已读'))?.click())
      await wait(2200)
      const bellAfter = await page.evaluate(() => {
        const b = document.querySelector('.notice-btn .el-badge__content')
        return b ? b.textContent : ''
      })
      ok('商家「全部已读」后铃铛清零', !bellAfter, `before=${bellBefore} after=${bellAfter}`)
    }
    await page.keyboard.press('Escape').catch(() => {})
    await wait(400)

    // 3) 展开学生分组 -> 会话窗口
    await page.click('.cs-group-head')
    await wait(2000)
    const rows = await page.$$eval('.cs-group-sessions .cs-session', els => els.length)
    ok('展开学生看到多个会话窗口', rows >= 1, `${rows} 个`)
    ok('会话行有删除按钮', !!(await page.$('.cs-group-sessions .cs-session .cs-session-del')))
    ok('分组有删除全部会话按钮', !!(await page.$('.cs-group-head .cs-group-del')))

    // 选中一个会话（自动已读）
    await page.click('.cs-group-sessions .cs-session')
    await wait(2200)

    // 4) 输入区：附菜单 + 附优惠券
    ok('输入区有「附菜单」', await page.evaluate(() =>
      Array.from(document.querySelectorAll('.cs-chat-input button')).some(b => b.innerText.includes('附菜单'))))
    const couponBtn = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.cs-chat-input button')).find(b => b.innerText.includes('附优惠券')))
    ok('输入区有「附优惠券」', !!couponBtn)

    // 5) 客服设置：发券规则字段齐全
    await page.$$eval('.cs-chat-tools button', els => els.find(b => b.innerText.includes('客服设置'))?.click())
    await wait(1200)
    const configFields = await page.evaluate(() => {
      const dialog = document.querySelector('.el-dialog')
      return dialog ? dialog.innerText.replace(/\s+/g, ' ') : ''
    })
    for (const label of ['AI 可发券', '手动发券', '折扣区间', '允许免单', '发券冷却', '老客门槛']) {
      ok(`客服设置有「${label}」`, configFields.includes(label))
    }
    // 关闭设置
    await page.$$eval('.el-dialog__footer button', els => {
      const b = els.find(x => x.innerText.includes('取消'))
      if (b) b.click()
    }).catch(() => {})
    await wait(800)

    // 6) 手动发券：打开弹窗 -> 选 8 折 -> 发券 -> 成功
    await page.$$eval('.cs-chat-input button', els => els.find(b => b.innerText.includes('附优惠券'))?.click())
    await wait(1000)
    const options = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.coupon-options .el-radio')).map(e => e.innerText.trim()))
    ok('发券弹窗有折扣档位', options.length > 0, JSON.stringify(options))
    const picked = await page.evaluate(() => {
      const radio = Array.from(document.querySelectorAll('.coupon-options .el-radio')).find(r => r.innerText.includes('8 折'))
      if (radio) { radio.click(); return true }
      return false
    })
    ok('可选 8 折', picked)
    await wait(300)
    await page.$$eval('.el-dialog__footer button', els => {
      const b = els.find(x => x.innerText.includes('券卡片'))
      if (b) b.click()
    })
    await wait(3000)
    const successToast = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.el-message')).map(e => e.innerText).join(' | '))
    ok('手动发券成功提示', successToast.includes('券'), successToast.slice(0, 80))
    const lastMsg = await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('.msg-row'))
      return rows.length ? rows[rows.length - 1].innerText.replace(/\s+/g, ' ') : ''
    })
    ok('会话里出现发券消息', lastMsg.includes('券'), lastMsg.slice(0, 80))

    // 7) 删除一个会话（UI）
    const before = await page.$$eval('.cs-group-sessions .cs-session', els => els.length)
    await page.click('.cs-group-sessions .cs-session .cs-session-del')
    await wait(900)
    const confirmed = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.el-message-box__btns button'))
      const b = btns.find(x => x.innerText.includes('删除'))
      if (b) { b.click(); return true }
      return false
    })
    await wait(2200)
    const after = await page.$$eval('.cs-group-sessions .cs-session', els => els.length)
    ok('商家 UI 删除会话生效', confirmed && after === before - 1, `${before} -> ${after}`)
  } catch (e) {
    console.log('ERROR', e.message)
  } finally {
    const failed = results.filter(r => !r[1]).length
    console.log(`\n==== ${results.length - failed}/${results.length} passed ====`)
    await browser.close()
  }
})()