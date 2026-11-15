const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(150000)
  const R=[]; const ok=(n,c,d)=>{R.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  async function login(role){await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900);await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),role).catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{});await p.waitForNavigation({waitUntil:'networkidle2',timeout:20000}).catch(()=>{});await wait(2000)}
  // ---- 退款定位 ----
  await login('管理员')
  await p.goto(BASE+'/admin/refunds?refer=REFUND&id=11',{waitUntil:'networkidle0'})
  {const t0=Date.now();let seen=false;while(Date.now()-t0<10000){if(await p.evaluate(()=>!!document.querySelector('.flash-target'))){seen=true;break}await wait(200)}
   ok('定位-退款 高亮', seen)}

  // ---- 敏感词屏蔽（学生评价）----
  await login('学生')
  const mod = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token'); const h = {'Content-Type':'application/json','Authorization':'Bearer '+t}
    // 找一道可评价的菜（直接调提交评价接口）
    const r = await fetch('/api/dishes/1/reviews', { method:'POST', headers:h, body: JSON.stringify({ rating:5, content:'你这个傻逼店家，妈的', anonymous:0 }) })
    return await r.json()
  })
  console.log('REVIEW SUBMIT:', JSON.stringify(mod))
  const noti = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token')
    const r = await fetch('/api/notifications?limit=5', { headers:{'Authorization':'Bearer '+t} })
    const j = await r.json()
    return (j.data||[]).map(n=>({type:n.type,content:(n.content||'').slice(0,60)}))
  })
  console.log('NOTIFICATIONS:', JSON.stringify(noti))
  const warn = noti.find(n=>n.type==='WARN')
  ok('敏感评价被屏蔽 + 学生收到WARN通知', !!warn, warn ? warn.content : '无WARN')
  ok('学生话术为模型生成(非旧固定文案)', warn && !warn.content.includes('请注意文明用语'), warn?warn.content:'')
  // 检查评价状态=2
  // ---- 开关关闭后放行 ----
  await b.close()
  console.log('\n=== 通过 '+R.filter(Boolean).length+' / '+R.length+' ===')
})()
