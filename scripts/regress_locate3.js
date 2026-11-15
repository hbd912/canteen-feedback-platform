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
  async function jumpAndCheck(url, label, extraSel) {
    await p.goto(url,{waitUntil:'networkidle0'})
    const t0=Date.now(); let seen=false
    while(Date.now()-t0<12000){ if(await p.evaluate((s)=>!!document.querySelector(s), extraSel||'.flash-target')){seen=true;break} await wait(200) }
    const info = await p.evaluate(()=>({url:location.href, flashes:document.querySelectorAll('.flash-target').length, rows:document.querySelectorAll('.el-table__row,.review-item,.wmc-stall').length}))
    ok(label, seen, JSON.stringify(info))
    return seen
  }
  await jumpAndCheck(BASE+'/admin/feedback-center?tab=review&refer=REVIEW&id=88','定位-低分评价 高亮')
  await jumpAndCheck(BASE+'/admin/feedback-center?tab=merchant&refer=MERCHANT_MESSAGE&id=10','定位-商家留言 高亮')
  await jumpAndCheck(BASE+'/admin/dish-audits?refer=DISH_AUDIT&id=117','定位-待审菜品 高亮')
  console.log('\n=== 通过 '+R.filter(Boolean).length+' / '+R.length+' ===')
  await b.close()
})()
