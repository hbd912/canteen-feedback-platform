const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  const out = []
  const ok = (n,c,d)=>{out.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'管理员').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{})
  await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)

  // 处理前工作台
  const before = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const res = await fetch('/api/admin/workbench', { headers: { 'Authorization': 'Bearer ' + t } })
    const j = await res.json()
    return { todos: j.data.todos.map(x => x.title + ':' + x.count) }
  })
  console.log('BEFORE:', JSON.stringify(before))

  // 点全部处理
  await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2200)
  await p.$$eval('.pad-btn',els=>els[0]?.click()).catch(()=>{});await wait(800)
  await p.$$eval('.chat-primary',els=>els.find(e=>e.innerText.includes('开始整理'))?.click()).catch(()=>{})
  await p.waitForFunction(()=>document.querySelector('.agent-ask'),{timeout:60000}).catch(()=>{})
  await wait(1200)
  const pre = await p.evaluate(()=>({need:document.querySelector('.split-num.need')?.innerText}))
  await p.$$eval('.agent-btn',els=>els.find(e=>e.innerText.includes('全部处理'))?.click()).catch(()=>{})
  await p.waitForFunction(()=>document.querySelectorAll('.insight-bubble').length>=2,{timeout:120000}).catch(()=>{})
  await wait(2500)
  const after = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const res = await fetch('/api/admin/workbench', { headers: { 'Authorization': 'Bearer ' + t } })
    const j = await res.json()
    const bubbles = document.querySelectorAll('.insight-bubble')
    const last = bubbles[bubbles.length-1]
    return { todos: j.data.todos.map(x => x.title + ':' + x.count),
      need: last?.querySelector('.split-num.need')?.innerText,
      summary: last?.querySelector('.chat-text')?.innerText }
  })
  console.log('AFTER:', JSON.stringify(after, null, 2))
  ok('全部处理后「需你手动」清空', after.need === '0', `need=${after.need}`)
  ok('工作台留言/评价待办归零', !after.todos.some(t=>t.includes('留言')||t.includes('低分评价')), JSON.stringify(after.todos))
  await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/final-handle-all.png'})
  console.log('通过 '+out.filter(Boolean).length+' / '+out.length)
  await b.close()
})().catch(e => console.log('ERR', e.message))
