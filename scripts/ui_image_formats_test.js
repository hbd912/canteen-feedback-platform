/* 图片格式兼容：BMP + 7.9MB 大 PNG 应被前端转成 JPEG 后成功上传 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
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
    await page.goto(BASE + '/merchant/ai-assistant', { waitUntil: 'networkidle0' }); await wait(2500)

    for (const [label, path] of [['BMP', 'D:/AI/AI Contest/canteen-feedback/.tmp-test/dish_test.bmp'], ['7.9MB PNG', 'D:/AI/AI Contest/canteen-feedback/.tmp-test/big_dish.png']]) {
      const input = await page.$('.wz-upload input[type=file]')
      await input.uploadFile(path)
      // 等预览图出现（转换需要一点时间）
      let okUpload = false
      for (let i = 0; i < 20; i++) {
        await wait(500)
        okUpload = await page.evaluate(() => !!document.querySelector('.wz-pending-img'))
        if (okUpload) break
      }
      ok(`${label} 上传并转换成功`, okUpload)
      // 清掉当前待上传图，避免叠加
      await page.evaluate(() => {
        const img = document.querySelector('.wz-pending-img')
        if (img) img.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      })
      await wait(300)
      // 重新加载页面重置状态
      await page.reload({ waitUntil: 'networkidle0' }); await wait(1800)
    }
  } catch (e) { console.log('ERROR', e.message) } finally {
    console.log(`\n==== ${results.filter(Boolean).length}/${results.length} passed ====`)
    await browser.close()
  }
})()
