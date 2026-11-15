const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(900)
  await p.$$eval('[class*=pill]',(els,n)=>els.find(x=>x.innerText.includes(n))?.click(),'商家').catch(()=>{});await wait(250);await p.click('.submit-btn').catch(()=>{})
  await p.waitForNavigation({waitUntil:'networkidle2'}).catch(()=>{});await wait(2000)
  // 模拟真实草稿提交：带图片
  const r = await p.evaluate(async () => {
    const t = localStorage.getItem('canteen_token'); const h = {'Content-Type':'application/json','Authorization':'Bearer '+t}
    const res = await fetch('/api/dishes', { method:'POST', headers:h, body: JSON.stringify({
      name:'图片验证菜', price:11, category:'主食', description:'带图验证', image:'/api/uploads/23ca077af9c944fbb77776d336a0ef79.jpg', featured:0, status:1, stockQty:null, soldOut:0
    })})
    return await res.json()
  })
  console.log('CREATE WITH IMAGE:', JSON.stringify(r))
  await p.goto(BASE+'/merchant/dishes',{waitUntil:'networkidle0'});await wait(2800)
  const pv = await p.evaluate(()=>({
    hasPending: document.body.innerText.includes('图片验证菜'),
    imgLoaded: [...document.querySelectorAll('.pv-dish')].some(el => {
      const nm = el.querySelector('.pv-dish-name')?.innerText
      const img = el.querySelector('img')
      return nm === '图片验证菜' && img && img.getAttribute('src')?.includes('/uploads/')
    }),
    badges: [...document.querySelectorAll('.pv-draft-badge')].map(x=>x.innerText)
  }))
  console.log('PREVIEW:', JSON.stringify(pv))
  await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/preview-with-image.png'})
  await b.close()
})().catch(e => console.log('ERR', e.message))
