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
    // 1) 悬浮助手已删除
    await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2200)
    const noFab = await p.evaluate(()=>!document.querySelector('.chat-widget'))
    ok('悬浮助手已删除', noFab)

    // 2) 值班助理标题无 emoji，用形象图
    const pad = await p.evaluate(()=>({title:document.querySelector('.pad-title')?.innerText, img:!!document.querySelector('.pad-avatar')}))
    ok('标题无 emoji + 保留形象图', !pad.title.includes('🤖') && pad.img, JSON.stringify(pad))

    // 3) 展开 + 开始整理 → 分类展示（需你手动 / 可自动处理）+ agent 选择
    await p.$$eval('.pad-btn',els=>els[0]?.click()).catch(()=>{});await wait(800)
    await p.$$eval('.chat-primary',els=>els.find(e=>e.innerText.includes('开始整理'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelector('.agent-ask'),{timeout:45000}).catch(()=>console.log('NO agent-ask'))
    await wait(1500)
    const classify = await p.evaluate(()=>({
      needLabel: document.querySelector('.need-label')?.innerText,
      autoLabel: document.querySelector('.auto-label')?.innerText,
      agentAsk: document.querySelector('.agent-ask-text')?.innerText,
      btnAll: [...document.querySelectorAll('.agent-btn')].some(x=>x.innerText.includes('全部处理')),
      btnAuto: [...document.querySelectorAll('.agent-btn')].some(x=>x.innerText.includes('只处理免审核的')),
      splitNeed: document.querySelector('.split-num.need')?.innerText,
      splitAuto: document.querySelector('.split-num.auto')?.innerText
    }))
    ok('分类标题「需要你手动」', classify.needLabel?.includes('需要你手动'), classify.needLabel)
    ok('分类标题「可以自动处理」', classify.autoLabel?.includes('可以自动处理'), classify.autoLabel)
    ok('agent 问话', classify.agentAsk?.includes('全部处理')||classify.agentAsk?.includes('只处理'), classify.agentAsk)
    ok('两个选择按钮', classify.btnAll && classify.btnAuto, `all=${classify.btnAll} auto=${classify.btnAuto}`)
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/agent-classify.png'})

    // 4) 定位：点第一个 needAction 项 → tab 保留 + 高亮
    await p.$$eval('.priority-row',els=>els[0]?.click()).catch(()=>{})
    await wait(3000)
    const jumped = p.url()
    const flashed = await p.evaluate(()=>!!document.querySelector('.flash-target'))
    ok('定位跳转 + 高亮', /refer=/.test(jumped)&&/id=/.test(jumped)&&flashed, `${jumped} flashed=${flashed}`)
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/agent-locate.png'})

    // 5) 只处理免审核的 flow
    await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2200)
    await p.$$eval('.pad-btn',els=>els[0]?.click()).catch(()=>{});await wait(800)
    await p.$$eval('.chat-primary',els=>els.find(e=>e.innerText.includes('开始整理'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelector('.agent-ask'),{timeout:45000}).catch(()=>{})
    await wait(1200)
    await p.$$eval('.agent-btn',els=>els.find(e=>e.innerText.includes('只处理免审核的'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelectorAll('.insight-bubble').length>=2,{timeout:45000}).catch(()=>{})
    await wait(1500)
    const autoDone = await p.evaluate(()=>document.body.innerText.includes('已自动处理')||document.body.innerText.includes('免审核'))
    ok('「只处理免审核的」触发并汇报', autoDone)
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/agent-auto-only.png'})
  } catch(e){ok('运行异常',false,e.message)}
  console.log('\n=== 汇总 ===');console.log('通过 '+results.filter(Boolean).length+' / '+results.length)
  await b.close();process.exit(results.every(Boolean)?0:1)
})()
