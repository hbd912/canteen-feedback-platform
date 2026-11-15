/* 经营助理技能栏 + 真实图片上传上架 + 菜品管理/档口预览合并 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const API = 'http://localhost:8080/api'
const wait = ms => new Promise(r => setTimeout(r, ms))
async function api(p, payload, method, token) {
  const h = { Authorization: 'Bearer ' + (token || '') }
  let b
  if (payload !== undefined && payload !== null) { h['Content-Type'] = 'application/json'; b = JSON.stringify(payload) }
  return (await fetch(API + p, { method: method || (payload ? 'POST' : 'GET'), headers: h, body: b })).json()
}
;(async () => {
  const mt = (await api('/auth/login', { username: '100012', password: '123456' }, 'POST')).data
  const before = (await api('/dishes?stallId=1&current=1&size=100', null, 'GET', mt.token)).data.total
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 950 } })
  const page = await browser.newPage(); page.setDefaultTimeout(45000)
  const results = []; const ok = (n, c, d) => { results.push(!!c); console.log((c ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')) }
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await page.evaluate(() => localStorage.clear())
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2200)

    // 1) 经营助理技能栏
    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2500)
    const chips = await page.$$eval('.skill-chip', els => els.map(e => e.innerText.replace(/\s+/g, ' ').trim()))
    ok('技能栏包含上传菜品/经营问答', chips.some(c => c.includes('上传菜品')) && chips.some(c => c.includes('经营问答')), JSON.stringify(chips))
    ok('默认展示上传菜品面板', await page.evaluate(() => !!document.querySelector('.wz-panel')))

    // 2) 上传真实菜品图 -> 生成草稿
    const input = await page.$('.wz-upload input[type=file]')
    ok('存在图片上传入口', !!input)
    await input.uploadFile('D:/AI/AI Contest/canteen-feedback/gourmet food/10015.jpg')
    await wait(2500)
    const pending = await page.evaluate(() => !!document.querySelector('.wz-pending-img'))
    ok('图片上传成功并预览', pending)
    await page.click('.wz-input .ap-btn--brand')
    // 等草稿卡片出现（视觉模型 1~4 秒，留足余量）
    await page.waitForSelector('.wz-draft', { timeout: 50000 })
    await wait(1200)
    const draft = await page.evaluate(() => {
      const el = document.querySelector('.wz-draft')
      return el ? {
        text: el.innerText.replace(/\s+/g, ' '),
        price: el.querySelector('.wz-draft-price-row input')?.value
      } : null
    })
    ok('视觉模型生成草稿', !!draft && draft.text.length > 10, draft && draft.text.slice(0, 80))
    ok('草稿带建议价格', draft && Number(draft.price) > 0, draft && draft.price)
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/assistant-upload-draft.png' })

    // 3) 满意上架
    await page.$$eval('.wz-btn-row .el-button', els => els.find(b => b.innerText.includes('满意'))?.click())
    await wait(3000)
    ok('上架成功', (await page.$$eval('.wz-uploaded', els => els.length)) > 0)
    const after = (await api('/dishes?stallId=1&current=1&size=100', null, 'GET', mt.token)).data.total
    ok('菜品数 +1', after === before + 1, `${before} -> ${after}`)

    // 4) 切到经营问答
    await page.$$eval('.skill-chip', els => els.find(b => b.innerText.includes('经营问答'))?.click())
    await wait(800)
    ok('可切换到经营问答', await page.evaluate(() => !!document.querySelector('.assistant-card') && getComputedStyle(document.querySelector('.assistant-card')).display !== 'none'))

    // 5) 菜品管理 / 档口预览合并
    await page.goto(BASE + '/merchant/dishes', { waitUntil: 'networkidle0' }); await wait(2500)
    const pills = await page.$$eval('.mode-switch .ap-pill', els => els.map(e => e.innerText.trim()))
    ok('菜品管理页有管理/预览切换', pills.includes('菜品管理') && pills.includes('档口预览'), JSON.stringify(pills))
    await page.$$eval('.mode-switch .ap-pill', els => els.find(b => b.innerText.includes('档口预览'))?.click())
    await wait(2500)
    ok('预览显示档口信息和菜品墙', await page.evaluate(() => !!document.querySelector('.pv-hero') && document.querySelectorAll('.pv-dish').length > 0),
      await page.evaluate(() => document.querySelectorAll('.pv-dish').length + ' 道'))
    await page.click('.pv-dish'); await wait(1000)
    const detailBtns = await page.evaluate(() => Array.from(document.querySelectorAll('.el-dialog__footer button')).map(b => b.innerText.trim()))
    ok('预览详情只有编辑/关闭，没有下架删除', detailBtns.includes('去编辑') && !detailBtns.some(t => t.includes('下架') || t.includes('删除')), JSON.stringify(detailBtns))
    await page.$$eval('.el-dialog__footer button', els => els.find(b => b.innerText.includes('去编辑'))?.click())
    await wait(1200)
    ok('从预览去编辑会打开编辑表单', await page.evaluate(() => Array.from(document.querySelectorAll('.el-dialog')).some(d => d.innerText.includes('编辑菜品') && getComputedStyle(d).display !== 'none')))
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/dishes-preview-mode.png' })
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
