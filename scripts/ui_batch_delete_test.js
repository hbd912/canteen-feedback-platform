/* 学生端 & 商家端 多选删除 UI 测试 */
const puppeteer = require('puppeteer-core')
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5180'
const wait = ms => new Promise(r => setTimeout(r, ms))
async function login(page, user) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(BASE + '/login', { waitUntil: 'networkidle0' }); await wait(1200)
  await page.$$eval('.pill-account', (els, u) => els.find(x => x.innerText.includes(u))?.click(), user); await wait(300)
  await page.click('.submit-btn')
  await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {})
  await wait(2200)
}
async function run(page, url, groupSel, label) {
  await page.goto(BASE + url, { waitUntil: 'networkidle0' }); await wait(3000)
  await page.click('.cs-group-head'); await wait(1800)
  const before = await page.$$eval('.cs-group-sessions .cs-session', els => els.length)
  await page.click('.cs-manage-btn'); await wait(600)
  const boxes = await page.$$eval('.cs-sel-box', els => els.length)
  console.log(label, '管理模式勾选框出现:', boxes > 0)
  // 选中前两个会话
  await page.$$eval('.cs-group-sessions .cs-session', els => { els[0]?.click(); els[1]?.click() })
  await wait(500)
  const picked = await page.evaluate(() => document.querySelectorAll('.cs-sel-box.on').length)
  console.log(label, '已选:', picked)
  const btnText = await page.evaluate(() => document.querySelector('.cs-batch-del')?.innerText || '')
  console.log(label, '批量按钮:', btnText)
  await page.click('.cs-batch-del'); await wait(800)
  await page.evaluate(() => {
    const btns = Array.from(document.querySelectorAll('.el-message-box__btns button'))
    btns.find(b => b.innerText.includes('删除'))?.click()
  })
  await wait(2500)
  const after = await page.$$eval('.cs-group-sessions .cs-session', els => els.length)
  console.log(label, `会话数 ${before} -> ${after}`, after === before - 2 ? 'PASS' : 'FAIL')
  // 退出管理模式，确认恢复普通点击
  await page.click('.cs-manage-btn').catch(() => {})
  await wait(400)
}
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1440, height: 900 } })
  const page = await browser.newPage(); page.setDefaultTimeout(30000)
  await login(page, 'student01')
  await run(page, '/customer-service', '.cs-group-head', '[学生]')
  await login(page, '100012')
  await run(page, '/merchant/customer-service', '.cs-group-head', '[商家]')
  await browser.close()
})()
