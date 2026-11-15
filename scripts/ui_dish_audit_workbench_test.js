/* 验收：工作台对话(AI值班) + 退款只读 + 菜品审核 三块新功能 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const SHOTS = 'D:/AI/AI Contest/canteen-feedback/screenshots'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(45000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  const errors = []; p.on('pageerror', e => errors.push(e.message))
  try {
    // 管理员登录
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1100)
    await p.$$eval('.pill-account', els => els.find(x => x.innerText.includes('管理员'))?.click()).catch(() => {}); await wait(300)
    await p.click('.submit-btn'); await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2000)

    // 1) 侧栏：菜品审核 + 退款记录 菜单项
    const menu = await p.$$eval('.app-menu .el-menu-item', els => els.map(e => e.innerText.trim()))
    ok('侧栏含「菜品审核」', menu.some(t => t.includes('菜品审核')), menu.join('|'))
    ok('侧栏为「退款记录」且无退款审核', menu.some(t => t.includes('退款记录')) && !menu.some(t => t.includes('退款审核')), menu.join('|'))

    // 2) 退款只读页
    await p.goto(BASE + '/admin/refunds', { waitUntil: 'networkidle0' }); await wait(1800)
    const refundPage = await p.evaluate(() => ({
      title: document.querySelector('.head-title')?.innerText || document.querySelector('.ap-title')?.innerText || '',
      body: document.body.innerText,
      actionBtns: [...document.querySelectorAll('button')].map(b => b.innerText.trim()).filter(t => t === '通过' || t === '驳回' || t.includes('批准') || t.includes('退款'))
    }))
    ok('退款页标题为退款记录', refundPage.title.includes('退款记录'), refundPage.title)
    ok('退款页含只读提示', refundPage.body.includes('只读记录') || refundPage.body.includes('不参与'), '')
    ok('退款页无通过/驳回操作', !['通过', '驳回'].some(t => refundPage.actionBtns.includes(t)), refundPage.actionBtns.join(',') || 'none')
    await p.screenshot({ path: SHOTS + '/admin-refunds-readonly.png' })

    // 3) 菜品审核页
    await p.goto(BASE + '/admin/dish-audits', { waitUntil: 'networkidle0' }); await wait(2000)
    const auditPage = await p.evaluate(() => ({
      title: document.querySelector('.ap-title')?.innerText || '',
      pills: [...document.querySelectorAll('.ap-pill')].map(e => e.innerText.trim()),
      rows: document.querySelectorAll('.audit-table .el-table__row').length,
      pending: [...document.querySelectorAll('.audit-table .el-table__row')].filter(r => r.innerText.includes('待审核') || r.innerText.includes('等待审核')).length
    }))
    ok('菜品审核页标题正确', auditPage.title.includes('菜品审核'), auditPage.title)
    ok('菜品审核状态筛选齐全', ['待审核','已通过','已驳回'].every(x => auditPage.pills.includes(x)), auditPage.pills.join('|'))

    // 4) 工作台：待处理 + AI 值守对话组件
    await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2500)
    const wb = await p.evaluate(() => ({
      title: document.querySelector('.wb-title')?.innerText || '',
      stats: document.querySelectorAll('.wb-stat, .stat').length,
      hasChat: !!document.querySelector('.ai-chat, .chat-panel, [class*=chat]'),
      body: document.body.innerText
    }))
    ok('工作台标题与统计卡', wb.title.length > 0 && wb.stats >= 3, `title=${wb.title} stats=${wb.stats}`)
    ok('工作台含 AI 值守对话区', wb.hasChat || wb.body.includes('开始整理'), `chat=${wb.hasChat}`)
    await p.screenshot({ path: SHOTS + '/admin-workbench-chat.png' })
  } catch (e) {
    ok('运行异常', false, e.message)
  }
  console.log('\n=== 汇总 ===')
  console.log('通过 ' + results.filter(Boolean).length + ' / ' + results.length)
  if (errors.length) console.log('JS 错误:\n' + errors.slice(0, 5).join('\n'))
  await b.close()
  process.exit(results.every(Boolean) ? 0 : 1)
})()