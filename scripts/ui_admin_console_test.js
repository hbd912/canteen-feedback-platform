/* 后台工作台 + 用户/商家合并页 UI 测试 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(45000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  const errors = []; p.on('pageerror', e => errors.push(e.message))
  try {
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1100)
    await p.$$eval('.pill-account', els => els.find(x => x.innerText.includes('管理员'))?.click()); await wait(300)
    await p.click('.submit-btn'); await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2000)

    // 1) 工作台：4 张指标、无资质入口、无快捷入口
    await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2200)
    const wb = await p.evaluate(() => ({
      title: document.querySelector('.wb-title')?.innerText || '',
      stats: document.querySelectorAll('.wb-stat').length,
      rows: [...document.querySelectorAll('.wb-row')].map(e => e.innerText),
      quick: document.querySelectorAll('.wb-quick, .wb-pill').length,
      hasCertText: document.body.innerText.includes('商家资质待审核') || document.body.innerText.includes('待审商家资质')
    }))
    ok('工作台指标与待办渲染', wb.title.length > 0 && wb.stats === 4 && wb.rows.length >= 1,
      `title=${wb.title} stats=${wb.stats} rows=${wb.rows.length}`)
    ok('工作台不再出现资质审核与快捷入口', !wb.hasCertText && wb.quick === 0,
      `cert=${wb.hasCertText} quick=${wb.quick}`)
    await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/admin-workbench-v3.png' })

    // 2) 菜单：只有「用户与商家」，没有「商家入驻审核」/「商家管理」/「用户管理」独立项
    const menuTexts = await p.$$eval('.app-menu .el-menu-item', els => els.map(e => e.innerText.trim()))
    ok('侧栏合并为「用户与商家」且无入驻审核',
      menuTexts.includes('用户与商家') && !menuTexts.includes('商家入驻审核') && !menuTexts.includes('商家管理') && !menuTexts.includes('用户管理'),
      menuTexts.join(' | '))
    ok('AI 运营已并入客服中心（菜单无 AI 运营）',
      menuTexts.includes('客服中心') && !menuTexts.includes('AI 运营'), menuTexts.join(' | '))

    // 3) 合并页：默认用户管理，可切商家管理
    await p.goto(BASE + '/admin/members', { waitUntil: 'networkidle0' }); await wait(2200)
    const tabs = await p.$$eval('.center-tabs .el-tabs__item', els => els.map(e => e.innerText.trim()))
    const active1 = await p.$eval('.center-tabs .el-tabs__item.is-active', e => e.innerText.trim()).catch(() => '')
    ok('合并页顶部有用户/商家两个标签', tabs.includes('用户管理') && tabs.includes('商家管理') && active1 === '用户管理',
      `tabs=${tabs.join('|')} active=${active1}`)
    await p.$$eval('.center-tabs .el-tabs__item', els => els.find(e => e.innerText.includes('商家管理'))?.click()); await wait(900)
    const active2 = await p.$eval('.center-tabs .el-tabs__item.is-active', e => e.innerText.trim()).catch(() => '')
    const stallVisible = await p.evaluate(() => {
      const pane = [...document.querySelectorAll('.el-tab-pane')].find(x => x.innerText.includes('商家管理') && x.getBoundingClientRect().height > 0)
      return !!pane
    })
    ok('点击可切到商家管理内容', active2 === '商家管理' && stallVisible, `active=${active2} visible=${stallVisible}`)

    // 4) 旧链接兼容
    await p.goto(BASE + '/admin/stalls', { waitUntil: 'networkidle0' }); await wait(1600)
    const urlStalls = p.url()
    const activeStalls = await p.$eval('.center-tabs .el-tabs__item.is-active', e => e.innerText.trim()).catch(() => '')
    ok('旧链接 /admin/stalls 跳到商家管理标签', urlStalls.includes('/admin/members') && activeStalls === '商家管理',
      `${urlStalls} active=${activeStalls}`)
    await p.goto(BASE + '/admin/users', { waitUntil: 'networkidle0' }); await wait(1600)
    const activeUsers = await p.$eval('.center-tabs .el-tabs__item.is-active', e => e.innerText.trim()).catch(() => '')
    ok('旧链接 /admin/users 跳到用户管理标签', p.url().includes('/admin/members') && activeUsers === '用户管理',
      `${p.url()} active=${activeUsers}`)

    // 5) 客服中心：两工作区，知识与记录各自统一操作
    await p.goto(BASE + '/admin/cs-center', { waitUntil: 'networkidle0' }); await wait(2000)
    const csTabs = await p.$$eval('.service-segments [role="tab"]', els => els.map(e => e.innerText.trim()))
    ok('客服中心收敛为两个工作区', csTabs.length === 2 && csTabs.includes('知识与回复') && csTabs.includes('会话与记录'), csTabs.join(' | '))

    // 6) 旧 AI 运营链接兼容
    await p.goto(BASE + '/admin/ai-ops', { waitUntil: 'networkidle0' }); await wait(1800)
    const activeAi = await p.$eval('.service-segments [aria-selected="true"]', e => e.innerText.trim()).catch(() => '')
    ok('旧链接进入知识工作区并筛选有效回复', p.url().includes('/admin/cs-center') && p.url().includes('kind=EXPERIENCE') && activeAi === '知识与回复',
      `${p.url()} active=${activeAi}`)

    const jsErr = errors.filter(e => !/favicon|net::|Failed to load resource/.test(e))
    ok('无 JS 报错', jsErr.length === 0, jsErr.slice(0, 3).join(' | '))
  } catch (e) {
    console.log('FAIL 异常', e.message)
    results.push(false)
  } finally { await b.close() }
  const pass = results.filter(Boolean).length
  console.log('---')
  console.log(`${pass}/${results.length} passed`)
  process.exit(pass === results.length ? 0 : 1)
})().catch(e => { console.error('ERR', e.message); process.exit(1) })