const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'管理员').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{})
  await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)
  // 直接看接口返回
  const api = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const res = await fetch('/api/admin/workbench/ai-insight/greeting', { headers: { 'Authorization': 'Bearer ' + t } })
    return await res.json()
  })
  console.log('GREETING API:', JSON.stringify(api))
  // 展开值班助理看欢迎语
  await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2200)
  await p.$$eval('.pad-btn',els=>els[0]?.click()).catch(()=>{})
  await wait(3500)
  const welcome = await p.evaluate(() => document.querySelector('.chat-text')?.innerText)
  console.log('WELCOME BUBBLE:', welcome)
  await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/greeting-dynamic.png'})
  await b.close()
})().catch(e => console.log('ERR', e.message))
