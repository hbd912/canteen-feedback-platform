const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(150000)
  const R=[]; const ok=(n,c,d)=>{R.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  async function login(role){await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900);await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),role).catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{});await p.waitForNavigation({waitUntil:'networkidle2',timeout:20000}).catch(()=>{});await wait(2000)}
  try {
    await login('管理员')
    // ---- A 工作台待办统计 ----
    await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2500)
    const wb = await p.evaluate(()=>({sub:document.querySelector('.wb-sub')?.innerText,rows:[...document.querySelectorAll('.wb-row')].map(x=>x.innerText.replace(/\s+/g,' ').slice(0,30))}))
    ok('工作台待办含留言/评价/菜品', wb.rows.some(r=>r.includes('留言')) && wb.rows.some(r=>r.includes('评价')) && wb.rows.some(r=>r.includes('菜品')), JSON.stringify(wb.rows))
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/rg-workbench.png'})

    // ---- B 值班助理欢迎语（模型生成）+ 分类 ----
    await p.$$eval('.pad-btn',els=>els[0]?.click()).catch(()=>{});await wait(3500)
    const welcome = await p.evaluate(()=>document.querySelector('.chat-text')?.innerText||'')
    ok('欢迎语动态生成(非原模板)', !!welcome && !welcome.includes('只有食品安全、卫生、退款这类敏感问题才列出来让你定位确认'), welcome.slice(0,60))
    await p.$$eval('.chat-primary',els=>els.find(e=>e.innerText.includes('开始整理'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelector('.agent-ask'),{timeout:90000}).catch(()=>{})
    await wait(1500)
    const cls = await p.evaluate(()=>({need:document.querySelector('.split-num.need')?.innerText,auto:document.querySelector('.split-num.auto')?.innerText,ask:!!document.querySelector('.agent-ask'),needRows:document.querySelectorAll('.priority-row').length}))
    ok('分类 + agent 问话', cls.ask && (Number(cls.need)>0), JSON.stringify(cls))
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/rg-classify.png'})

    // ---- C 定位（逐类验证） ----
    // C1: 学生留言
    await p.$$eval('.priority-row',els=>{const r=els.find(x=>x.innerText.includes('留言')||x.innerText.includes('食品安全')||x.innerText.includes('卫生'));if(r)r.click()}).catch(()=>{})
    await wait(3500)
    const c1 = await p.evaluate(()=>({url:location.href,flash:!!document.querySelector('.flash-target')}))
    ok('定位-留言/评价类 高亮', c1.flash, JSON.stringify(c1))

    // ---- D 全部处理 ----
    await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2200)
    await p.$$eval('.pad-btn',els=>els[0]?.click()).catch(()=>{});await wait(3000)
    await p.$$eval('.chat-primary',els=>els.find(e=>e.innerText.includes('开始整理'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelector('.agent-ask'),{timeout:90000}).catch(()=>{})
    await wait(1200)
    await p.$$eval('.agent-btn',els=>els.find(e=>e.innerText.includes('全部处理'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelectorAll('.insight-bubble').length>=2,{timeout:180000}).catch(()=>{})
    await wait(2500)
    const after = await p.evaluate(async()=>{
      const t=localStorage.getItem('canteen_token')
      const wb=await (await fetch('/api/admin/workbench',{headers:{'Authorization':'Bearer '+t}})).json()
      return {todos:wb.data.todos.map(x=>x.title+':'+x.count)}
    })
    ok('全部处理后台待办清空(仅剩菜品审核)', !after.todos.some(t=>t.includes('留言')||t.includes('评价')), JSON.stringify(after.todos))
    console.log('  todos after:', JSON.stringify(after.todos))
  } catch(e){ ok('运行异常',false,e.message) }
  console.log('\n=== 通过 '+R.filter(Boolean).length+' / '+R.length+' ===')
  await b.close()
})()
