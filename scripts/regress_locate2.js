const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(150000)
  const R=[]; const ok=(n,c,d)=>{R.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'管理员').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{})
  await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)
  async function pollFlash(label) {
    const t0 = Date.now()
    while (Date.now() - t0 < 12000) {
      const f = await p.evaluate(()=>!!document.querySelector('.flash-target'))
      if (f) { ok(label, true, '出现于 ' + ((Date.now()-t0)/1000).toFixed(1) + 's'); return }
      await wait(200)
    }
    // 找不到也记录目标行是否存在
    const info = await p.evaluate(()=>({url:location.href, items:document.querySelectorAll('.feedback-item').length}))
    ok(label, false, JSON.stringify(info))
  }
  // 找到一条真实存在的 feedback id
  const fid = await p.evaluate(async()=>{const t=localStorage.getItem('canteen_token')
    const res=await fetch('/api/feedbacks/admin?current=1&size=20',{headers:{'Authorization':'Bearer '+t}}).catch(()=>null)
    if(!res) return null
    const j=await res.json().catch(()=>null)
    return j?.data?.records?.[0]?.id || null})
  console.log('feedback id:', fid)
  await p.goto(BASE+'/admin/feedback-center?tab=student&refer=FEEDBACK&id='+(fid||46),{waitUntil:'networkidle0'})
  await pollFlash('定位-FEEDBACK 高亮')
  await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/rg-locate-fb.png'})
  console.log('\n=== 通过 '+R.filter(Boolean).length+' / '+R.length+' ===')
  await b.close()
})()
