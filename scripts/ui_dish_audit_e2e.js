/* 端到端：商家新建菜品→进入待审核→管理员通过→上架；并演示驳回流程 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const SHOTS = 'D:/AI/AI Contest/canteen-feedback/screenshots'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(50000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  const tombstone = '验收菜品-' + Date.now().toString(36).slice(-6)

  async function login(accountRole) {
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1100)
    const needle = accountRole === 'merchant' ? '100012' : 'admin'
    await p.$$eval('.pill-account', (els, n) => els.find(x => x.innerText.includes(n))?.click(), needle).catch(() => {})
    await wait(300)
    await p.type('.submit-btn', '')  // noop
    await p.click('.submit-btn'); await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {}); await wait(2200)
  }

  try {
    // ---- 步骤1：商家登录，用 API 新建一道菜（走 createDish 的审核路径：商家→PENDING/status=0）----
    // 先在浏览器里以商家身份调用原始 fetch，模拟商家端菜品管理新增
    await login('merchant')
    const created = await p.evaluate(async ({ tombstone }) => {
      const token = localStorage.getItem('canteen_token') || ''
      const resp = await fetch('/api/dishes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
        body: JSON.stringify({
          stallId: 1, name: tombstone, price: 15.9, category: '验收', description: '端到端验收菜品',
          image: '', featured: 0, stockQty: null, soldOut: 0
        })
      })
      const data = await resp.json().catch(() => null)
      return { status: resp.status, data }
    }, { tombstone })
    const newId = created?.data?.data
    ok('商家新建菜品返回成功', !!newId, `id=${newId} status=${created?.status}`)

    // 步骤2：管理员登录，进入菜品审核页，确认待审核列表里有这道菜
    await login('admin')
    await p.goto(BASE + '/admin/dish-audits', { waitUntil: 'networkidle0' }); await wait(2500)
    const inPending = await p.evaluate((tombstone) =>
      [...document.querySelectorAll('.audit-table .el-table__row')].some(r => r.innerText.includes(tombstone)),
      tombstone)
    ok('菜品出现在管理员待审核列表', inPending, tombstone)
    await p.screenshot({ path: SHOTS + '/audit-pending.png' })

    // 步骤3：点「通过」，确认上架
    const approved = await p.evaluate(async ({ newId }) => {
      const token = localStorage.getItem('canteen_token') || ''
      const resp = await fetch('/api/admin/dish-audits/' + newId + '/approve', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token } })
      return resp.status
    }, { newId })
    ok('管理员审核通过接口成功', approved === 200, `http=${approved}`)
    // 刷新看已通过：页面默认在待审核，点「已通过」标签
    await p.goto(BASE + '/admin/dish-audits', { waitUntil: 'networkidle0' }); await wait(2000)
    await p.$$eval('.ap-pill', els => els.find(e => e.innerText.includes('已通过'))?.click()).catch(() => {}); await wait(1800)
    const inApproved = await p.evaluate((tombstone) =>
      [...document.querySelectorAll('.audit-table .el-table__row')].some(r => r.innerText.includes(tombstone)),
      tombstone)
    ok('通过后菜品进入已通过列表', inApproved, tombstone)

    // 步骤4：学生端可见性检查——已在售（status=1 + APPROVED）
    const visible = await p.evaluate(async ({ newId }) => {
      const resp = await fetch('/api/dishes/' + newId)
      const data = await resp.json().catch(() => null)
      const d = data?.data?.dish || data?.data
      return d ? { status: d.status, audit: d.auditStatus } : null
    }, { newId })
    ok('通过后学生端可见(状态在售)', visible && visible.status === 1 && visible.audit === 'APPROVED', JSON.stringify(visible))

    // 步骤5：驳回演示——再建一道，管理员驳回填原因
    await login('merchant')
    const tomb2 = '驳回菜品-' + Date.now().toString(36).slice(-6)
    const created2 = await p.evaluate(async ({ tomb2 }) => {
      const token = localStorage.getItem('canteen_token') || ''
      const resp = await fetch('/api/dishes', {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
        body: JSON.stringify({ stallId: 1, name: tomb2, price: 12, category: '验收', description: '驳回演示', image: '', featured: 0, stockQty: null, soldOut: 0 })
      })
      const data = await resp.json().catch(() => null)
      return data?.data
    }, { tomb2 })
    ok('第二道菜创建成功', !!created2, `id=${created2}`)

    await login('admin')
    const rejected = await p.evaluate(async ({ id2 }) => {
      const token = localStorage.getItem('canteen_token') || ''
      const resp = await fetch('/api/admin/dish-audits/' + id2 + '/reject', {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
        body: JSON.stringify({ reason: '图片信息不完整，请补充后再提交' })
      })
      return resp.status
    }, { id2: created2 })
    ok('管理员驳回接口成功', rejected === 200, `http=${rejected}`)
    await p.goto(BASE + '/admin/dish-audits', { waitUntil: 'networkidle0' }); await wait(2000)
    await p.$$eval('.ap-pill', els => els.find(e => e.innerText.includes('已驳回'))?.click()).catch(() => {}); await wait(1800)
    const inRejected = await p.evaluate((tomb2) => {
      const row = [...document.querySelectorAll('.audit-table .el-table__row')].find(r => r.innerText.includes(tomb2))
      return { found: !!row, text: row?.innerText || '' }
    }, tomb2)
    ok('驳回后进入已驳回列表并显示原因', inRejected.found && inRejected.text.includes('图片信息不完整'), inRejected.text.slice(0, 60))
    await p.screenshot({ path: SHOTS + '/audit-rejected.png' })

  } catch (e) {
    ok('运行异常', false, e.message)
  }
  console.log('\n=== 汇总 ===')
  console.log('通过 ' + results.filter(Boolean).length + ' / ' + results.length)
  await b.close()
  process.exit(results.every(Boolean) ? 0 : 1)
})()