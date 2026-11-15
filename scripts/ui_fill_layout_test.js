/* 列表页铺满高度验收：卡片撑满可视区、翻页在视口内 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))

async function login(page, role) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1000)
  const needle = role === 'admin' ? '管理员' : '100012'
  await page.$$eval('.pill-account', (els, n) => els.find(x => x.innerText.includes(n))?.click(), needle)
  await wait(300)
  await page.click('.submit-btn')
  await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(1600)
}

async function measure(page) {
  return page.evaluate(() => {
    const panes = [...document.querySelectorAll('.el-tab-pane')].filter(p => p.getBoundingClientRect().height > 0)
    const scope = panes.length ? panes[0] : document
    // 主体可能是卡片、自适应表格容器，或底部翻页；取这些块的最下沿判断是否铺满
    const blocks = [...scope.querySelectorAll('.page-card, .adaptive-table, .el-pagination')]
      .map(el => el.getBoundingClientRect())
    if (!blocks.length) return null
    const bottom = Math.max(...blocks.map(r => r.bottom))
    const height = Math.max(...blocks.map(r => r.height))
    const pagers = [...scope.querySelectorAll('.el-pagination')].map(e => e.getBoundingClientRect())
    const pagerBottom = pagers.length ? Math.max(...pagers.map(r => r.bottom)) : null
    return {
      bottom: Math.round(bottom),
      height: Math.round(height),
      winH: window.innerHeight,
      pagerIn: pagerBottom === null ? null : pagerBottom <= window.innerHeight + 2
    }
  })
}

const results = []
const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }

async function check(page, label) {
  await wait(1200)
  const m = await measure(page)
  const filled = !!m && m.bottom >= m.winH - 40 && m.height > 280
  ok(label + ' 卡片铺满', filled, m ? `bottom=${m.bottom}/${m.winH} h=${m.height}` : '无卡片')
  if (m && m.pagerIn !== null) {
    ok(label + ' 翻页在视口内', m.pagerIn, `pagerIn=${m.pagerIn}`)
  }
}

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(45000)
  try {
    // ---------- 管理员 ----------
    await login(p, 'admin')

    await p.goto(BASE + '/admin/members', { waitUntil: 'networkidle0' }); await wait(1600)
    await check(p, '用户管理')

    await p.$$eval('.center-tabs .el-tabs__item', els => els.find(e => e.innerText.includes('商家管理'))?.click())
    await check(p, '商家管理')

    await p.goto(BASE + '/admin/cs-center', { waitUntil: 'networkidle0' }); await wait(1600)
    await check(p, '客服中心-知识与回复')
    await p.click('#records-tab')
    await check(p, '客服中心-会话与记录')

    await p.goto(BASE + '/admin/feedback-center', { waitUntil: 'networkidle0' }); await wait(1600)
    await check(p, '反馈-学生留言')
    await p.$$eval('.center-tabs .el-tabs__item', els => els.find(e => e.innerText.includes('商家留言'))?.click())
    await check(p, '反馈-商家留言')
    await p.$$eval('.center-tabs .el-tabs__item', els => els.find(e => e.innerText.includes('评价审核'))?.click())
    await check(p, '反馈-评价审核')

    for (const [path, label] of [['/admin/refunds', '退款审核'], ['/admin/notices', '公告管理'], ['/admin/banners', '轮播广告']]) {
      await p.goto(BASE + path, { waitUntil: 'networkidle0' })
      await check(p, label)
    }

    await p.goto(BASE + '/admin/cs-center?tab=knowledge&kind=EXPERIENCE', { waitUntil: 'networkidle0' }); await wait(1600)
    await check(p, '客服中心-有效回复筛选')

    // ---------- 小窗口 ----------
    await p.setViewport({ width: 1100, height: 720 })
    await p.goto(BASE + '/admin/members', { waitUntil: 'networkidle0' }); await wait(1600)
    await check(p, '小窗口-用户管理')
    await p.goto(BASE + '/admin/cs-center', { waitUntil: 'networkidle0' }); await wait(1000)
    await check(p, '小窗口-知识与回复')
    await p.click('#records-tab')
    await check(p, '小窗口-会话与记录')
    await p.setViewport({ width: 1440, height: 1000 })

    // ---------- 商家 ----------
    await login(p, 'merchant')
    await p.goto(BASE + '/merchant/reviews', { waitUntil: 'networkidle0' }); await wait(1800)
    await check(p, '商家-学生评价')
    await p.goto(BASE + '/merchant/refunds', { waitUntil: 'networkidle0' }); await wait(1600)
    await check(p, '商家-退款处理')
  } catch (e) {
    console.log('FAIL 异常', e.message)
    results.push(false)
  } finally { await b.close() }
  const pass = results.filter(Boolean).length
  console.log('---')
  console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e.message); process.exit(1) })