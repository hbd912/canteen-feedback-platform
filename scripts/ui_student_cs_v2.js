/* 学生端客服 UI 走查 v2：右上角新建、按档口分组、会话删除、已读红点、铃铛全部已读。
 * 前置：后端 8080、前端 5180。用法: node scripts/ui_student_cs_v2.js
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

/** 让学生先开一个新会话（确保未被学生删除），再由商家发一条消息制造未读 */
async function merchantSendUnread() {
  const stu = (await api('/auth/login', { username: 'student01', password: '123456' }, 'POST')).data
  const ns = (await api('/customer-service/sessions', { stallId: 1, newSession: true }, 'POST', stu.token)).data
  if (!ns || !ns.id) return null
  const mer = (await api('/auth/login', { username: '100012', password: '123456' }, 'POST')).data
  await api(`/customer-service/sessions/${ns.id}/messages`,
    { content: '同学在吗？今天的菜刚出锅，热乎的～' + Date.now() }, 'POST', mer.token)
  return ns.id
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

  const gotoCs = async () => {
    await page.goto(BASE + '/customer-service', { waitUntil: 'networkidle0' })
    await wait(3000)
  }

  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('student01'))?.click())
    await wait(300)
    await page.click('.submit-btn')
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2500)
    await gotoCs()

    // 1) 新建会话按钮在页面右上角
    const btn = await page.evaluate(() => {
      const b = document.querySelector('.cs-page-head .cs-new-session')
      if (!b) return null
      const r = b.getBoundingClientRect()
      return { x: r.x, vw: window.innerWidth }
    })
    ok('页头右上角存在「新建会话」', btn && btn.x > btn.vw / 2, btn ? `x=${Math.round(btn.x)} vw=${btn.vw}` : '未找到')
    ok('左栏不再有新建按钮', !(await page.$('.cs-side-head .cs-new-session')))

    // 2) 第一层按档口分组
    const groups = await page.$$eval('.cs-group-head', els => els.map(e => e.innerText.replace(/\s+/g, ' ').trim()))
    ok('会话已按档口分组', groups.length >= 1, JSON.stringify(groups.slice(0, 3)))
    ok('分组头显示档口名', groups[0] && groups[0].includes('川味小炒'), groups[0] || '')

    // 3) 铃铛：造未读 -> 刷新 -> 全部已读后数字与提示行消失
    await merchantSendUnread()
    await gotoCs()
    const bellBefore = await page.evaluate(() => {
      const b = document.querySelector('.notice-btn .el-badge__content')
      return b ? b.textContent : ''
    })
    ok('铃铛出现未读数字', !!bellBefore, `badge=${bellBefore}`)
    await page.click('.notice-btn')
    await wait(1200)
    const hasReadAll = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.notice-panel button')).some(b => b.innerText.includes('全部已读')))
    ok('铃铛有「全部已读」按钮', hasReadAll)
    if (hasReadAll) {
      await page.$$eval('.notice-panel button', els => els.find(b => b.innerText.includes('全部已读'))?.click())
      await wait(2500)
      const bellAfter = await page.evaluate(() => {
        const b = document.querySelector('.notice-btn .el-badge__content')
        return b ? b.textContent : ''
      })
      const csRow = await page.evaluate(() => document.querySelectorAll('.notice-panel .notice-item.unread').length)
      ok('「全部已读」后铃铛数字消失', !bellAfter, `before=${bellBefore} after=${bellAfter}`)
      ok('「全部已读」后客服未读行消失', csRow === 0, `剩余未读行=${csRow}`)
    }
    // 收起铃铛
    await page.keyboard.press('Escape').catch(() => {})
    await wait(400)

    // 4) 分组红点：再造未读 -> 刷新 -> 展开分组即已读
    await merchantSendUnread()
    await gotoCs()
    const groupBadgeBefore = await page.evaluate(() => {
      const b = document.querySelector('.cs-group-head .el-badge__content')
      return b ? b.textContent : ''
    })
    ok('分组头出现未读红点', !!groupBadgeBefore, `badge=${groupBadgeBefore}`)
    await page.click('.cs-group-head')
    await wait(2200)
    const rows = await page.$$eval('.cs-group-sessions .cs-session', els => els.length)
    const groupBadgeAfter = await page.evaluate(() => {
      const b = document.querySelector('.cs-group-head .el-badge__content')
      return b ? b.textContent : ''
    })
    ok('展开分组后显示会话窗口', rows >= 1, `${rows} 个`)
    ok('进入会话后分组红点消失', !groupBadgeAfter, `before=${groupBadgeBefore} after=${groupBadgeAfter}`)
    ok('会话行有删除按钮', !!(await page.$('.cs-group-sessions .cs-session .cs-session-del')))

    // 5) 删除会话（UI）：计数减 1
    const before = await page.$$eval('.cs-group-sessions .cs-session', els => els.length)
    if (before >= 1) {
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
      ok('UI 删除会话生效', confirmed && after === before - 1, `${before} -> ${after}`)
    }

    ok('学生端没有手动发券按钮', !(await page.$('.coupon-btn')))
  } catch (e) {
    console.log('ERROR', e.message)
  } finally {
    const failed = results.filter(r => !r[1]).length
    console.log(`\n==== ${results.length - failed}/${results.length} passed ====`)
    await browser.close()
  }
})()