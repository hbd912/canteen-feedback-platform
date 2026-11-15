/* 商家待办页「✨ AI 一键处理」UI 走查：登录商家 -> /merchant/todos -> 点 AI 一键处理 -> 等报告弹窗 */
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
    await page.goto(BASE + '/merchant/todos', { waitUntil: 'networkidle0' });
    await wait(3000);

    const btn = await page.$('.todo-head-right .el-button:not([disabled])')
        .then(async b => {
          if (!b) return null;
          const txt = await page.evaluate(el => el.innerText, b);
          return txt.includes('一键处理') ? b : null;
        });
    console.log('「✨ AI 一键处理」按钮可点:', !!btn);
    const todosText = await page.evaluate(() => (document.querySelector('.todo-list, .todo-head, body')?.innerText || '').slice(0, 150));
    console.log('待办区:', todosText.replace(/\n/g, ' | '));

    if (btn) {
      await btn.click();
      const t0 = Date.now();
      // POST 应立即返回：等待「AI 正在自动处理」提示或 loading 出现
      await wait(1500);
      console.log('点击后 1.5s 页面未卡死(异步生效), loading 提示可见:', await page.evaluate(() => !!document.querySelector('.el-loading-mask, .el-message')));
      // 轮询报告弹窗
      let sawReport = false;
      for (let i = 0; i < 25; i++) {
        await wait(2000);
        if (await page.$('.el-dialog')) {
          const d = await page.evaluate(() => {
            const el = document.querySelector('.el-dialog');
            const vis = el && getComputedStyle(el).display !== 'none';
            return vis ? el.innerText.slice(0, 300) : null;
          });
          if (d) { sawReport = true; console.log('完成弹窗:', d.replace(/\n/g, ' | ')); break; }
        }
        if (i % 5 === 0) console.log(`  轮询 ${i*2}s... running 提示:`, await page.evaluate(() => !!document.querySelector('.el-message--info')));
      }
      console.log('AI 处理完成弹窗出现?', sawReport, '  总耗时~', (Date.now()-t0)/1000 + 's');
    }
    await browser.close();
  } catch (e) {
    console.log('错误:', e.message);
    await browser.close();
  }
})();