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
  // 看看 localStorage 里有没有草稿
  const ls = await p.evaluate(() => localStorage.getItem('canteen_dish_drafts'))
  console.log('DRAFTS LS:', ls ? ls.slice(0,200) : 'NULL/EMPTY')
  // 打开经营助理，看草稿卡
  await p.goto(BASE+'/merchant/ai-assistant',{waitUntil:'networkidle0'});await wait(3000)
  const cards = await p.evaluate(()=>document.querySelectorAll('.draft-card').length)
  console.log('DRAFT CARDS in assistant:', cards)
  // 打开菜品管理预览
  await p.goto(BASE+'/merchant/dishes',{waitUntil:'networkidle0'});await wait(3000)
  const pv = await p.evaluate(()=>({
    draftBadges: document.querySelectorAll('.pv-draft-badge').length,
    cards: document.querySelectorAll('.pv-card, [class*=pv-]').length,
    draftsLS: localStorage.getItem('canteen_dish_drafts')
  }))
  console.log('PREVIEW:', JSON.stringify(pv).slice(0,300))
  await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/preview-check.png'})
  await b.close()
})().catch(e => console.log('ERR', e.message))
