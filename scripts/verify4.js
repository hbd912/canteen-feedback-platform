const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  const results=[]; const ok=(n,c,d)=>{results.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  async function login(role){await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900);await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),role).catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{});await p.waitForNavigation({waitUntil:'networkidle2',timeout:20000}).catch(()=>{});await wait(2000)}
  try {
    await login('管理员')
    // 1) 折叠卡 + emoji + 话术
    await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2500)
    const pad = await p.evaluate(()=>({title:document.querySelector('.pad-title')?.innerText,sub:document.querySelector('.pad-sub')?.innerText}))
    ok('折叠卡含 emoji 标题「🤖 值班助理」', pad.title?.includes('🤖'), pad.title)
    ok('话术「我帮你处理吧」', pad.sub?.includes('我帮你处理吧'), pad.sub)

    // 2) 展开 + 定位到 review 标签页（验证 tab 参数不再丢失）
    await p.$$eval('.pad-btn',els=>els[0]?.click()).catch(()=>{});await wait(800)
    await p.$$eval('.chat-primary',els=>els.find(e=>e.innerText.includes('开始整理'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelectorAll('.priority-row').length>0,{timeout:45000}).catch(()=>{})
    await wait(1800)
    // 点击第一个 needAction 项（可能是 review 或 feedback），记录 URL 应保留 tab
    await p.$$eval('.priority-row',els=>els[0]?.click()).catch(()=>{})
    await wait(3000)
    const jumped = p.url()
    ok('定位跳转 URL 含 refer 与 id', /refer=/.test(jumped) && /id=/.test(jumped), jumped)
    // 如果跳到 feedback-center，tab 应保留
    if (jumped.includes('feedback-center')) {
      ok('跳转保留 tab 参数', /tab=/.test(jumped), jumped)
    }
    const flashed = await p.evaluate(()=>!!document.querySelector('.flash-target'))
    ok('目标页高亮闪烁 .flash-target', flashed, `flashed=${flashed}`)
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/locate-fixed.png'})

    // 3) 直接处理 flow（回到工作台重新展开）
    await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2200)
    await p.$$eval('.pad-btn',els=>els[0]?.click()).catch(()=>{});await wait(800)
    await p.$$eval('.chat-primary',els=>els.find(e=>e.innerText.includes('开始整理'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelectorAll('.priority-row').length>0||document.querySelector('.auto-strip'),{timeout:45000}).catch(()=>{})
    await wait(1500)
    // 点「不用确认，直接帮我处理」快捷提问
    await p.$$eval('.quick-chip',els=>els.find(e=>e.innerText.includes('直接帮我处理'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelectorAll('.insight-bubble').length>=2,{timeout:45000}).catch(()=>{})
    await wait(1800)
    const direct = await p.evaluate(()=>document.body.innerText.includes('直接处理了')||document.body.innerText.includes('人工确认'))
    ok('「直接处理」触发了 handle-all 并返回汇总', direct)
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/handle-all.png'})

    // 4) 新建对话保留历史
    await p.$$eval('button',els=>els.find(x=>x.innerText.includes('新建对话'))?.click()).catch(()=>{})
    await wait(700)
    const hist = await p.evaluate(()=>({divider:document.body.innerText.includes('新会话'),stillHasInsight:document.querySelectorAll('.insight-bubble').length>0}))
    ok('新建对话保留历史（有分隔线 + 旧记录仍在）', hist.divider && hist.stillHasInsight, JSON.stringify(hist))
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/new-chat-history.png'})
  } catch(e){ok('运行异常',false,e.message)}
  console.log('\n=== 汇总 ===');console.log('通过 '+results.filter(Boolean).length+' / '+results.length)
  await b.close();process.exit(results.every(Boolean)?0:1)
})()
