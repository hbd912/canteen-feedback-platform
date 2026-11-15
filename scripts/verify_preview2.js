const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'商家').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{})
  await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)
  // 注入一条草稿到 localStorage，再刷新预览页
  await p.evaluate(() => {
    localStorage.setItem('canteen_dish_drafts', JSON.stringify([
      { localId:'t1', name:'测试草稿菜', price:9, category:'主食', description:'测试', image:'' }
    ]))
  })
  await p.goto(BASE+'/merchant/dishes',{waitUntil:'networkidle0'});await wait(2500)
  const r = await p.evaluate(()=>({
    badges: document.querySelectorAll('.pv-draft-badge').length,
    hasDraftName: document.body.innerText.includes('测试草稿菜'),
    count: document.querySelector('.pv-head .ap-section-title')?.innerText
  }))
  console.log('PREVIEW AFTER INJECT:', JSON.stringify(r))
  await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/preview-inject.png'})
  await b.close()
})().catch(e => console.log('ERR', e.message))
