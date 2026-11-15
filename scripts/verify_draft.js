const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'] })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'商家').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{})
  await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)
  const me = await p.evaluate(()=>{const u=JSON.parse(localStorage.getItem('canteen_user')||'{}');return {id:u.id,stallId:u.stallId,name:u.realName,role:u.role}})
  console.log('MERCHANT:', JSON.stringify(me))
  const r = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token'); const h = {'Content-Type':'application/json','Authorization':'Bearer '+t}
    const res = await fetch('/api/dishes', { method:'POST', headers:h, body: JSON.stringify({
      name:'香辣牛肉面', price:15, category:'主食', description:'劲道细面配大块卤牛肉', featured:0, status:1, stockQty:null, soldOut:0
    })})
    const j = await res.json()
    return { code:j.code, message:j.message, id:j.data }
  })
  console.log('CREATE DISH:', JSON.stringify(r))
  await b.close()
})().catch(e => console.log('ERR', e.message))
