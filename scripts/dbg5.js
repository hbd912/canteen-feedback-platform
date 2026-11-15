const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  p.on('console', m => { const t=m.text(); if(/error|fail|warn/i.test(t)) console.log('C:', t.slice(0,150)) })
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await p.evaluate(() => localStorage.clear())
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1000)
  await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), '管理员').catch(() => {})
  await wait(250); await p.click('.submit-btn').catch(() => {})
  await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {}); await wait(2000)
  await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2500)
  await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {})
  await wait(900)
  await p.$$eval('.chat-primary', els => els.find(e => e.innerText.includes('开始整理'))?.click()).catch(() => {})
  // also ensure no stale insight; force run
  await p.waitForFunction(() => document.querySelectorAll('.priority-row').length > 0, { timeout: 60000 }).catch(()=>console.log('no priority'))
  await wait(1500)
  const target = await p.evaluate(() => {
    const r = document.querySelector('.priority-row')
    // Vue router push path+query; derive from the insight's source — probe by reading all text
    return r ? r.innerText : null
  })
  console.log('PRIORITY ROW:', target?.replace(/\s+/g,' '))
  // read the needAction sourceType/sourceId that go() pushes — inspect via vue
  await p.$$eval('.priority-row', els => els[0]?.click()).catch(()=>console.log('click priority failed'))
  // go() pushes router; wait then read URL
  await wait(4000)
  console.log('URL after jump:', p.url())
  console.log('flash present:', await p.evaluate(() => !!document.querySelector('.flash-target')))
  console.log('feedback items:', await p.evaluate(() => document.querySelectorAll('.feedback-item').length))
  console.log('has id44:', await p.evaluate(() => !!document.querySelector('.feedback-item[data-recid="44"]')))
  await p.screenshot({ path: 'D:/AI/AI Contest/canteen-feedback/screenshots/dbg-flash.png' })
  await b.close()
})()
