const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(150000)
  const R=[]; const ok=(n,c,d)=>{R.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'学生').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{})
  await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)
  const mod = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token'); const h = {'Content-Type':'application/json','Authorization':'Bearer '+t}
    const r = await fetch('/api/reviews', { method:'POST', headers:h, body: JSON.stringify({ dishId:1, rating:5, content:'你这个傻逼店家，妈的', anonymous:0 }) })
    return await r.json()
  })
  console.log('REVIEW SUBMIT:', JSON.stringify(mod))
  await wait(2500)
  const noti = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const r = await fetch('/api/notifications?limit=8', { headers:{'Authorization':'Bearer '+t} })
    const j = await r.json()
    return (j.data||[]).map(n=>({type:n.type,content:(n.content||'').slice(0,70)}))
  })
  console.log('NOTIFICATIONS:', JSON.stringify(noti))
  const warn = noti.find(n=>n.type==='WARN')
  ok('敏感评价 -> 学生收到 WARN', !!warn, warn?warn.content:'无')
  ok('话术为模型生成(非旧固定)', warn && !warn.content.includes('请注意文明用语'), warn?warn.content:'')
  await b.close()
  console.log('\n=== 通过 '+R.filter(Boolean).length+' / '+R.length+' ===')
})()
