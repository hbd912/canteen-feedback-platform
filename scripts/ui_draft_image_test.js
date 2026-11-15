const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 950 } })
  const page = await browser.newPage(); page.setDefaultTimeout(60000)
  const res=[]; const ok=(n,c,d)=>{res.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
    await page.evaluate(() => localStorage.clear())
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
    await page.$$eval('.pill-account', els => els.find(x => x.innerText.includes('100012'))?.click()); await wait(300)
    await page.click('.submit-btn'); await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
    await wait(2200)
    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2200)
    const input = await page.$('.wz-upload input[type=file]')
    await input.uploadFile('D:/AI/AI Contest/canteen-feedback/gourmet food/10001.jpg')
    await wait(1800)
    await page.click('.wz-input .ap-btn--brand')
    await page.waitForSelector('.wz-draft', { timeout: 50000 }); await wait(600)
    const draft = await page.evaluate(() => JSON.parse(localStorage.getItem('canteen_dish_drafts') || '[]')[0] || null)
    ok('草稿存了图片地址', draft && draft.image && draft.image.includes('/uploads/'), draft && draft.image)
    await page.goto(BASE + '/merchant/dishes', { waitUntil: 'networkidle0' }); await wait(2500)
    const imgOk = await page.evaluate(() => {
      const img = document.querySelector('.pv-dish img')
      return img ? { src: img.getAttribute('src'), loaded: img.complete && img.naturalWidth > 0 } : null
    })
    ok('预览草稿显示真实图片', imgOk && imgOk.loaded, JSON.stringify(imgOk))
    // 预览详情去确认
    await page.click('.pv-dish'); await wait(800)
    const btns = await page.evaluate(() => Array.from(document.querySelectorAll('.el-dialog__footer button')).map(b => b.innerText.trim()))
    ok('草稿详情有「去确认上架」', btns.includes('去确认上架'), JSON.stringify(btns))
    await page.$$eval('.el-dialog__footer button', els => els.find(b => b.innerText.includes('去确认'))?.click())
    await wait(1200)
    ok('去确认跳经营助理', page.url().includes('ai-assistant'))
    // 清理草稿，避免残留
    await page.evaluate(() => localStorage.removeItem('canteen_dish_drafts'))
    await page.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/draft-preview-image.png' })
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${res.filter(Boolean).length}/${res.length} passed ====`)
    await browser.close()
  }
})()
