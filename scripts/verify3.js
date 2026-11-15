const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  const results = []; const ok = (n,c,d)=>{results.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  async function login(role){await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900);await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),role).catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{});await p.waitForNavigation({waitUntil:'networkidle2',timeout:20000}).catch(()=>{});await wait(2000)}
  try {
    await login('管理员')
    // 1) 评价审核页右上角 自动屏蔽开关
    await p.goto(BASE+'/admin/feedback-center?tab=review',{waitUntil:'networkidle0'});await wait(2500)
    const toggle = await p.evaluate(()=>{const t=document.querySelector('.auto-block-toggle');const s=document.querySelector('.auto-block-toggle .el-switch');return {toggle:!!t,switch:!!s,label:document.querySelector('.auto-block-label')?.innerText}})
    ok('评价审核页含自动屏蔽开关', toggle.toggle && toggle.switch, JSON.stringify(toggle))
    // 开关默认状态（已开启）
    const isOn = await p.evaluate(()=>document.querySelector('.auto-block-toggle .el-switch')?.className.includes('is-checked'))
    ok('自动屏蔽默认开启', isOn, `on=${isOn}`)
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/autoblock-toggle.png'})

    // 2) 工作台 值班助理 新建对话 + 优先级排名图
    await p.goto(BASE+'/admin/workbench',{waitUntil:'networkidle0'});await wait(2200)
    await p.$$eval('.pad-btn',els=>els[0]?.click()).catch(()=>{})
    await wait(800)
    const hasNewChat = await p.evaluate(()=>[...document.querySelectorAll('button')].some(x=>x.innerText.includes('新建对话')))
    ok('值班助理含「新建对话」', hasNewChat)
    // 展开后点击开始整理
    await p.$$eval('.chat-primary',els=>els.find(e=>e.innerText.includes('开始整理'))?.click()).catch(()=>{})
    await p.waitForFunction(()=>document.querySelectorAll('.priority-row').length>0||document.querySelector('.auto-strip'),{timeout:45000}).catch(()=>{})
    await wait(1800)
    const chart = await p.evaluate(()=>({split:!!document.querySelector('.insight-split'),chartHead:document.querySelector('.chart-head span')?.innerText,need:document.querySelector('.split-num.need')?.innerText,auto:document.querySelector('.split-num.auto')?.innerText}))
    ok('优先级排名图 + 需拍板/自动处理分布', chart.split && chart.chartHead?.includes('优先级排名'), JSON.stringify(chart))
    await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/chat-priority-chart.png'})

    // 3) 新建对话重置
    await p.$$eval('button',els=>els.find(x=>x.innerText.includes('新建对话'))?.click()).catch(()=>{})
    await wait(600)
    const reset = await p.evaluate(()=>({welcome:document.body.innerText.includes('开始整理当前待处理'),hasInsight:!!document.querySelector('.insight-split')}))
    ok('新建对话重置为欢迎态', reset.welcome && !reset.hasInsight, JSON.stringify(reset))
  } catch(e){ok('运行异常',false,e.message)}
  console.log('\n=== 汇总 ===');console.log('通过 '+results.filter(Boolean).length+' / '+results.length)
  await b.close();process.exit(results.every(Boolean)?0:1)
})()
