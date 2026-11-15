const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'] })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  async function login(role){await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900);await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),role).catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{});await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)}
  await login('管理员')
  const r = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const h = { 'Authorization': 'Bearer ' + t }
    const res = await fetch('/api/admin/workbench/ai-insight/refresh', { method: 'POST', headers: h })
    const j = await res.json()
    return { model: j.data && j.data.model, summary: (j.data && j.data.summary || '').slice(0, 200) }
  })
  console.log('REFRESH model=', r.model)
  console.log('SUMMARY=', r.summary)
  await b.close()
})().catch(e => console.log('ERR', e.message))
