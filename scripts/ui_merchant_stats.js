/* 商家「客户统计」页 UI 走查：登录商家 -> 打开 /merchant/customers -> 看分层数字是否渲染 */
const puppeteer = require('puppeteer-core');
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const BASE = 'http://localhost:5174';
(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1280, height: 1000 } });
  const page = await browser.newPage();
  page.setDefaultTimeout(30000);
  const wait = ms => new Promise(r => setTimeout(r, ms));
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' });
    await wait(1200);
    await page.$$eval('.pill-account', els => els.find(x => (x.innerText||'').includes('100012'))?.click());
    await wait(300);
    await page.click('.submit-btn');
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(()=>{});
    await wait(2000);
    console.log('登录后 URL:', page.url());

    await page.goto(BASE + '/merchant/customers', { waitUntil: 'networkidle0' });
    await wait(3000);
    // 抓关键数字与表格行
    const info = await page.evaluate(() => {
      const body = document.body.innerText;
      const find = (label) => {
        const m = body.match(new RegExp(label + '\\s*([0-9]+)'));
        return m ? m[1] : null;
      };
      // 抓数字卡片上的 新客/常客/老客/安抚券 和表头
      const cards = Array.from(document.querySelectorAll('.el-statistic, .stat-num, [class*=stat]')).map(e => e.innerText.trim()).filter(Boolean).slice(0, 15);
      return {
        hasNew: find('新客'), hasRegular: find('常客'), hasVeteran: find('老客'), hasDiscount: find('安抚券') || find('发券'),
        cards,
        hasTable: !!document.querySelector('.el-table'),
        rowText: (function(){ const t = document.querySelector('.el-table'); return t ? t.innerText.slice(0, 200) : ''; })(),
        hasErr: body.includes('错误') || body.includes('Failed') || body.includes('加载失败')
      };
    });
    console.log('客户统计页内容:', JSON.stringify(info, null, 1));
    await browser.close();
  } catch (e) {
    console.log('错误:', e.message);
    await browser.close();
  }
})();