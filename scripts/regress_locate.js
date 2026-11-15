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
  // 直接构造 jump URL 并监视 flash class 出现（MutationObserver 记录曾出现过的 .flash-target）
  async function testJump(url, label) {
    await p.goto(BASE+'/login',{waitUntil:'networkidle0'}).catch(()=>{})
    await p.evaluate((u)=>{window.__flashSeen=false
      new MutationObserver(()=>{if(document.querySelector('.flash-target'))window.__flashSeen=true})
        .observe(document.body,{subtree:true,attributes:true,attributeFilter:['class']})
      location.href=u
    }, url)
    await wait(5000)
    const seen = await p.evaluate(()=>window.__flashSeen || !!document.querySelector('.flash-target'))
    ok(label, seen, p.url())
    return seen
  }
  // 造一条待审菜品定位数据已存在（前面 seed 的测试待审菜品），全部处理可能已审核掉，重新 seed
  // FEEDBACK 定位
  await testJump(BASE+'/admin/feedback-center?tab=student&refer=FEEDBACK&id=46','定位-学生留言 高亮')
  // REVIEW 定位：先查一条低分评价 id
  const rid = await p.evaluate(async()=>{const t=localStorage.getItem('canteen_token');const r=await fetch('/api/reviews/admin?current=1&size=5&status=1',{headers:{'Authorization':'Bearer '+t}}).catch(()=>null);return null})
  // 直接查 DB 由外部注入更可靠，这里用已知 id 46 for feedback only.
  console.log('\n=== 通过 '+R.filter(Boolean).length+' / '+R.length+' ===')
  await b.close()
})()
