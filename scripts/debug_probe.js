const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const SHOTS = 'D:/AI/AI Contest/canteen-feedback/screenshots'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1000 } })
  const p = await b.newPage(); p.setDefaultTimeout(60000)
  p.on('console', m => { const t = m.text(); if (/error|fail/i.test(t)) console.log('CONSOLE:', t.slice(0,200)) })
  p.on('response', r => { if (r.status() >= 400) console.log('HTTP', r.status(), r.url().slice(0,90)) })
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1000)
  const pills = await p.evaluate(() => [...document.querySelectorAll('button, .pill-account, [class*=pill]')].map(e => e.innerText).filter(Boolean))
  console.log('LOGIN BUTTONS:', JSON.stringify(pills.slice(0,10)))
  await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), '管理员').catch(e => console.log('pill click err', e.message))
  await wait(300)
  const sub = await p.evaluate(() => [...document.querySelectorAll('button')].map(e => e.innerText.trim()).filter(Boolean))
  console.log('SUBMIT BTNS:', JSON.stringify(sub.slice(0,8)))
  await p.click('.submit-btn').catch(e => console.log('submit err', e.message))
  await p.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => console.log('nav timeout'))
  await wait(2500)
  console.log('URL after login:', p.url())
  const body = await p.evaluate(() => document.body.innerText.replace(/\s+/g,' ').slice(0,300))
  console.log('BODY:', body)
  await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }).catch(e=>console.log('goto err', e.message))
  await wait(3000)
  console.log('WORKBENCH URL:', p.url())
  const has = await p.evaluate(() => ({
    padCard: !!document.querySelector('.pad-card'),
    chats: document.querySelectorAll('.chat-body, .pad-card').length,
    body: document.body.innerText.replace(/\s+/g,' ').slice(0,300)
  }))
  console.log('WB:', JSON.stringify(has))
  await p.screenshot({ path: SHOTS + '/dbg-workbench.png' })
  await b.close()
})()
