const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(200000)
  const R=[]; const ok=(n,c,d)=>{R.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))}
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'商家').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{})
  await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)
  // 只传图不写文字（之前 400 的场景）
  const r = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token'); const h = {'Content-Type':'application/json','Authorization':'Bearer '+t}
    const res = await fetch('/api/merchant/ai-assistant/ask', { method:'POST', headers:h, body: JSON.stringify({
      question:'', image:'/api/uploads/23ca077af9c944fbb77776d336a0ef79.jpg', skill:'upload'
    })})
    const j = await res.json()
    return { code:j.code, message:j.message, hasDraft: !!j.data?.draft, draftName: j.data?.draft?.name, price: j.data?.draft?.price }
  })
  console.log('ASK(empty question + image):', JSON.stringify(r))
  ok('只传图不再报错并返回草稿', r.code===200 && r.hasDraft, JSON.stringify(r))
  await b.close()
  console.log('\n=== 通过 '+R.filter(Boolean).length+' / '+R.length+' ===')
})()
