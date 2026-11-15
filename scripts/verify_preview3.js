const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(180000)
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'商家').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{})
  await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)
  await p.goto(BASE+'/merchant/ai-assistant',{waitUntil:'networkidle0'});await wait(2500)
  // 找到上传 input，传一张生成的小 png
  const input = await p.$('input[type=file]')
  if (!input) { console.log('NO FILE INPUT'); await b.close(); return }
  // 生成 64x64 png
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAPUlEQVR4nO3BAQ0AAADCoPdPbQ43oAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAfg0mAAABY29tAAAAAElFTkSuQmCC','base64')
  await input.uploadFile({ name:'t.png', mimeType:'image/png', buffer: png }).catch(async () => {
    const fs = require('fs'); fs.writeFileSync('D:/AI/AI Contest/canteen-feedback/scripts/t.png', png); await input.uploadFile('D:/AI/AI Contest/canteen-feedback/scripts/t.png')
  })
  await wait(3000)
  // 点发送按钮
  const sent = await p.evaluate(()=>{
    const btns=[...document.querySelectorAll('button')]
    const b=btns.find(x=>x.innerText.includes('让 AI 上架'))
    if(b){b.click();return true} return false
  })
  console.log('SENT:', sent)
  await wait(15000)
  const mid = await p.evaluate(()=>({
    draftCards: document.querySelectorAll('.draft-card').length,
    ls: (localStorage.getItem('canteen_dish_drafts')||'').slice(0,120),
    body: document.body.innerText.includes('草稿')||document.body.innerText.includes('建议售价')
  }))
  console.log('AFTER GENERATE:', JSON.stringify(mid))
  // 点去预览
  await p.evaluate(()=>{const b=[...document.querySelectorAll('button')].find(x=>x.innerText.includes('档口预览'));if(b)b.click()})
  await wait(3000)
  const pv = await p.evaluate(()=>({
    url: location.href,
    badges: document.querySelectorAll('.pv-draft-badge').length,
    count: document.querySelector('.pv-head .ap-section-title')?.innerText,
    firstNames: [...document.querySelectorAll('.pv-dish-name')].slice(0,3).map(x=>x.innerText),
    lsLen: (localStorage.getItem('canteen_dish_drafts')||'').length
  }))
  console.log('PREVIEW:', JSON.stringify(pv))
  await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/preview-e2e.png'})
  await b.close()
})().catch(e => console.log('ERR', e.message))
