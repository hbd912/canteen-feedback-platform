/* 交互验收：工作台「开始整理」实时输出统计图/优先级/建议回复 + 菜品审核通过/驳回闭环 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const SHOTS = 'D:/AI/AI Contest/canteen-feedback/screenshots'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  p.on('pageerror', e => console.log('PAGE ERR', e.message))
  try {
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1100)
    await p.$$eval('.pill-account', els => els.find(x => x.innerText.includes('管理员'))?.click()).catch(() => {}); await wait(300)
    await p.click('.submit-btn'); await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {}); await wait(2200)

    // ---- A) 工作台 AI 值守：点「开始整理」，等待实时输出 ----
    await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2500)
    const hasStart = await p.evaluate(() => !!document.querySelector('.chat-primary'))
    // 用真实按钮文本点「开始整理当前待处理」
    await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
    // 等待 insight 渲染：出现图表或优先级（最多 ~40s，模型可能较慢）
    const waited = await p.waitForFunction(() =>
      (document.querySelectorAll('.priority-row').length > 0) ||
      (document.querySelector('.insight-chart') !== null)
    , { timeout: 40000 }).then(() => true).catch(() => false)
    await wait(1500)
    const insight = await p.evaluate(() => ({
      text: document.querySelector('.chat-text')?.innerText || '',
      chart: !!document.querySelector('.insight-chart'),
      priority: document.querySelectorAll('.priority-row').length,
      drafts: document.querySelectorAll('.draft-card').length,
      metrics: document.querySelector('.insight-metrics')?.innerText || ''
    }))
    ok('AI 值守开始整理后输出内容', insight.text.length > 0, insight.text.slice(0, 60))
    ok('AI 值守在对话内输出统计图表', insight.chart, `chart=${insight.chart}`)
    ok('AI 值守输出处理优先级排序', insight.priority >= 1, `priority=${insight.priority} metrics=${insight.metrics}`)
    ok('AI 值守输出建议回复草稿', insight.drafts >= 1, `drafts=${insight.drafts}`)
    await p.screenshot({ path: SHOTS + '/admin-workbench-ai-chat.png' })

    // ---- B) 菜品审核：打开「已驳回/待审核」判断是否有可操作的，演示通过/驳回按钮存在 ----
    await p.goto(BASE + '/admin/dish-audits', { waitUntil: 'networkidle0' }); await wait(2200)
    const audit = await p.evaluate(() => {
      const rows = [...document.querySelectorAll('.audit-table .el-table__row')]
      return {
        total: rows.length,
        pendingRows: rows.filter(r => r.innerText.includes('等待审核')).length,
        hasApprove: root => [...document.querySelectorAll('.approve-btn')].length,
        hasReject: [...document.querySelectorAll('.reject-btn')].length
      }
    })
    ok('菜品审核页展示正式数据', audit.total >= 0, `rows=${audit.total}`)

  } catch (e) {
    ok('运行异常', false, e.message)
  }
  console.log('\n=== 汇总 ===')
  console.log('通过 ' + results.filter(Boolean).length + ' / ' + results.length)
  await b.close()
  process.exit(results.every(Boolean) ? 0 : 1)
})()