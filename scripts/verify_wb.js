const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'] })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'管理员').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{});await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)
  const r = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const res = await fetch('/api/admin/workbench', { headers: { 'Authorization': 'Bearer ' + t } })
    const j = await res.json()
    return { pendingFeedbacks: j.data.pendingFeedbacks, pendingMessages: j.data.pendingMessages,
      todos: j.data.todos.map(x => x.title + ':' + x.count) }
  })
  console.log(JSON.stringify(r, null, 2))
  // 工作台页面渲染核对
  await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2200)
  const ui = await p.evaluate(() => ({
    sub: document.querySelector('.wb-sub')?.innerText,
    rows: [...document.querySelectorAll('.wb-row')].map(x => x.innerText.replace(/\s+/g,' ').slice(0,40))
  }))
  console.log('UI sub=', ui.sub)
  console.log('UI rows=', JSON.stringify(ui.rows))
  await b.close()
})().catch(e => console.log('ERR', e.message))
