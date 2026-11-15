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
  // 1) insight 是否包含菜品审核
  const ins = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token'); const h = {'Authorization':'Bearer '+t}
    const res = await fetch('/api/admin/workbench/ai-insight/refresh', { method:'POST', headers:h })
    const j = await res.json()
    return { total: j.data.totalItems, coverage: j.data.coverage.map(c=>c.label+':'+c.count),
      need: j.data.needAction.map(x=>x.title), auto: j.data.autoHandleable.map(x=>x.title), summary: j.data.summary }
  })
  console.log('INSIGHT:', JSON.stringify(ins, null, 2))
  // 2) 全部处理 → 菜品是否被审核
  const handled = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token'); const h = {'Authorization':'Bearer '+t}
    const res = await fetch('/api/admin/workbench/ai-insight/handle-all', { method:'POST', headers:h })
    const j = await res.json()
    const wb = await (await fetch('/api/admin/workbench', { headers:h })).json()
    return { summary: j.data.summary, todos: wb.data.todos.map(x=>x.title+':'+x.count) }
  })
  console.log('AFTER HANDLE-ALL:', JSON.stringify(handled, null, 2))
  await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/dish-audit-ai.png'})
  await b.close()
})().catch(e => console.log('ERR', e.message))
