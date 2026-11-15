const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const wait = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 1050 } })
  const p = await b.newPage(); p.setDefaultTimeout(120000)
  await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await p.evaluate(() => localStorage.clear()); await p.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(900)
  await p.$$eval('[class*=pill]', (els, n) => els.find(x => x.innerText.includes(n))?.click(), '管理员').catch(() => {}); await wait(250); await p.click('.submit-btn').catch(() => {})
  await p.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}); await wait(2000)
  await p.goto(BASE + '/admin/workbench', { waitUntil: 'networkidle0' }); await wait(2200)
  await p.$$eval('.pad-btn', els => els[0]?.click()).catch(() => {}); await wait(2500)
  const h1 = await p.evaluate(() => document.querySelector('.wb-chat').getBoundingClientRect().height)
  // 灌 8 条消息再测
  await p.evaluate(() => {
    // 通过发送快捷问题快速堆消息不可行，直接读数
  })
  await wait(500)
  const r = await p.evaluate(() => {
    const chat = document.querySelector('.wb-chat')
    const body = document.querySelector('.chat-body')
    const dock = document.querySelector('.chat-dock')
    return { chatH: chat?.getBoundingClientRect().height, bodyH: body?.clientHeight, bodyScrollH: body?.scrollHeight, dockH: dock?.getBoundingClientRect().height }
  })
  console.log('HEIGHTS:', JSON.stringify(r), 'initial chatH=', h1)
  await b.close()
})()
