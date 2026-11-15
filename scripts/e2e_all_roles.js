/**
 * 全端严格回归：
 * 1) 学生端：敏感评价屏蔽 + WARN 话术；正常留言
 * 2) 商家端：提交菜品 -> 管理员通知 + 商家AI留言进线程
 * 3) 商家端：档口预览显示待审菜品+图片
 * 4) 管理员：值班助理分类 / 公告建议 / 全部处理
 * 5) 处理结果通知：学生、商家都收到
 * 6) 商家留言 -> 管理员AI处理 -> 商家通知
 * 7) 公告授权发布 -> 学生可见
 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))

const R = []
const ok = (n, c, d) => { R.push({ n, c: !!c, d }); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
// 每次运行唯一后缀，避免重名/旧数据干扰
const TAG = String(Date.now()).slice(-6)
const DISH_NAME = '回归排骨' + TAG
const FB_TITLE = '回归建议' + TAG
const MM_TEXT = '排烟维修' + TAG

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(240000)

  async function login(role) {
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await p.evaluate(() => localStorage.clear())
    await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(900)
    await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), role).catch(() => {})
    await wait(250); await p.click('.submit-btn').catch(() => {})
    await p.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}); await wait(2000)
  }
  const api = (path, opts = {}) => p.evaluate(async (path, opts) => {
    const t = localStorage.getItem('canteen_token')
    const res = await fetch('/api' + path, {
      method: opts.method || 'GET',
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + t },
      body: opts.body ? JSON.stringify(opts.body) : undefined
    })
    return await res.json().catch(() => null)
  }, path, opts)

  try {
    // ============ 1. 学生端 ============
    await login('学生')
    // 1a 敏感评价（含辱骂）→ 应屏蔽 + WARN
    const bad = await api('/reviews', { method: 'POST', body: { dishId: 1, rating: 5, content: '你这个傻逼商家，妈的，难吃死了', anonymous: 0 } })
    ok('学生提交敏感评价接口成功', bad?.code === 200, JSON.stringify(bad?.data))
    await wait(2500)
    const stuNoti1 = await api('/notifications?limit=6')
    const warn = (stuNoti1?.data || []).find(n => n.type === 'WARN')
    ok('敏感评价→学生收到WARN通知', !!warn, warn?.content)
    ok('WARN话术为模型生成', !!warn && !warn.content.includes('请注意文明用语'), warn?.content)

    // 1b 正常留言（普通建议）
    const fb1 = await api('/feedbacks', { method: 'POST', body: { stallId: 1, type: 'SUGGEST', title: FB_TITLE, content: '希望增加更多素菜品种，价格再亲民一点' } })
    ok('学生提交正常留言成功', fb1?.code === 200, JSON.stringify(fb1?.data))

    // ============ 2. 商家端 ============
    await login('商家')
    // 2a 真实提交一道待审菜品（带图）
    const dish = await api('/dishes', { method: 'POST', body: { name: DISH_NAME, price: 16, category: '主食', description: '慢炖排骨，软烂入味', image: '/api/uploads/23ca077af9c944fbb77776d336a0ef79.jpg', featured: 0, status: 1, stockQty: null, soldOut: 0 } })
    ok('商家提交待审菜品成功', dish?.code === 200, `id=${dish?.data}`)
    const dishId = dish?.data

    // 2b 商家发一条留言给管理员
    const mm = await api('/merchant/messages', { method: 'POST', body: { stallId: 1, content: MM_TEXT + '：厨房排烟有问题，希望后勤尽快来看一下' } })
    ok('商家给管理员留言成功', mm?.code === 200, JSON.stringify(mm?.data))

    // 2c 档口预览显示待审菜品+图片
    await p.goto(BASE + '/merchant/dishes', { waitUntil: 'networkidle0' }); await wait(3000)
    const pv = await p.evaluate((dishName) => {
      const cards = [...document.querySelectorAll('.pv-dish')]
      const target = cards.find(c => (c.querySelector('.pv-dish-name')?.innerText || '').includes(dishName))
      return {
        found: !!target,
        badge: target?.querySelector('.pv-draft-badge')?.innerText || '',
        img: target?.querySelector('img')?.getAttribute('src') || '',
        bodyText: target?.innerText.replace(/\s+/g, ' ') || ''
      }
    }, DISH_NAME)
    ok('档口预览显示待审菜品', pv.found, pv.bodyText.slice(0, 40))
    ok('待审菜品带「待审核」徽标', pv.badge.includes('待审核'), pv.badge)
    ok('待审菜品图片正常加载', pv.img.includes('/uploads/'), pv.img)

    // ============ 3. 管理员端 ============
    await login('管理员')
    // 3a 工作台待办统计
    await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2500)
    const wb = await p.evaluate(() => ({
      sub: document.querySelector('.wb-sub')?.innerText,
      rows: [...document.querySelectorAll('.wb-row')].map(x => x.innerText.replace(/\s+/g, ' ').slice(0, 34))
    }))
    ok('工作台待办含菜品/留言', wb.rows.length > 0, wb.sub)

    // 3b 管理员通知：新菜待审核
    const adminNoti = await api('/notifications?limit=10')
    ok('管理员收到「新菜待审核」通知', (adminNoti?.data || []).some(n => (n.content || '').includes(DISH_NAME)),
      (adminNoti?.data || []).map(n => n.content).slice(0, 2).join(' | '))

    // 3c 商家AI留言进入管理员线程
    const thread = await api('/admin/merchant-comm/stalls/1/thread')
    const aiMsg = (thread?.data || []).find(m => m.authorRole === 'AI' && (m.content || '').includes(DISH_NAME))
    ok('商家AI上架申请留言进入管理员线程', !!aiMsg, aiMsg?.content?.slice(0, 50))

    // 3d 值班助理刷新 → 分类 + 公告建议
    const ins = await api('/admin/workbench/ai-insight/refresh', { method: 'POST' })
    const d = ins?.data || {}
    ok('值班助理统计含留言/菜品', (d.needActionCount + d.autoHandleableCount) > 0,
      `need=${d.needActionCount} auto=${d.autoHandleableCount}`)
    ok('汇报话术由模型生成(非固定模板)', !!d.summary && d.summary.length > 10 && !d.summary.startsWith('已统计：共'), d.summary?.slice(0, 60))

    // 3e UI：展开值班助理看欢迎语/分类/按钮
    await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {}); await wait(3500)
    const welcome = await p.evaluate(() => document.querySelector('.chat-text')?.innerText || '')
    ok('值班助理欢迎语动态生成', !!welcome && welcome.length > 5, welcome.slice(0, 40))
    await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
    await p.waitForFunction(() => document.querySelector('.agent-ask') || document.querySelector('.announce-card'), { timeout: 120000 }).catch(() => {})
    await wait(1500)
    const cls = await p.evaluate(() => ({
      need: document.querySelector('.split-num.need')?.innerText,
      auto: document.querySelector('.split-num.auto')?.innerText,
      ask: !!document.querySelector('.agent-ask'),
      announce: !!document.querySelector('.announce-card')
    }))
    ok('分类展示 + agent 问话/公告建议', cls.need !== undefined && (cls.ask || cls.announce), JSON.stringify(cls))
    await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/e2e-admin-classify.png' })

    // 3f 全部处理
    await p.$$eval('.agent-btn', els => els.find(e => e.innerText.includes('全部处理'))?.click()).catch(() => {})
    await p.waitForFunction(() => document.querySelectorAll('.insight-bubble').length >= 2, { timeout: 200000 }).catch(() => {})
    await wait(3000)
    const after = await api('/admin/workbench')
    ok('处理后工作台待办清零', (after?.data?.todos || []).length === 0, JSON.stringify((after?.data?.todos || []).map(t => t.title)))
    const handledSummary = await p.evaluate(() => {
      const bs = document.querySelectorAll('.insight-bubble')
      return bs[bs.length - 1]?.querySelector('.chat-text')?.innerText || ''
    })
    ok('AI处理完有简洁总结', !!handledSummary && handledSummary.length > 5, handledSummary.slice(0, 60))

    // ============ 4. 处理结果通知回各端 ============
    const adminNoti2 = await api('/notifications?limit=10')
    ok('管理员收到菜品AI审核结果通知', (adminNoti2?.data || []).some(n => (n.content || '').includes('AI 已审核菜品')), '')

    await login('学生')
    const stuNoti2 = await api('/notifications?limit=10')
    ok('学生收到留言处理结果通知', (stuNoti2?.data || []).some(n => (n.content || '').includes(FB_TITLE)),
      (stuNoti2?.data || []).map(n => n.content).slice(0, 2).join(' | '))

    await login('商家')
    const merNoti = await api('/notifications?limit=10')
    ok('商家收到菜品审核结果通知', (merNoti?.data || []).some(n => (n.content || '').includes(DISH_NAME)), '')
    ok('商家收到留言处理结果通知', (merNoti?.data || []).some(n => (n.content || '').includes(MM_TEXT)), '')

  } catch (e) {
    ok('运行异常', false, e.message)
  }

  const pass = R.filter(x => x.c).length
  console.log(`\n=== 通过 ${pass} / ${R.length} ===`)
  await b.close()
  process.exit(pass === R.length ? 0 : 1)
})()
