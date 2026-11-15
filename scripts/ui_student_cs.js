/* 学生端 AI 客服 UI 走查：登录 -> 客服会话 -> 新建会话 -> 发消息 -> 抓 AI 回复(SSE)。
 * 用 puppeteer-core 驱动本机 Chrome。用法: node scripts/ui_student_cs.js   (前端须在 5174)
 */
const puppeteer = require('puppeteer-core');
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const BASE = 'http://localhost:5174';

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1280,900'],
    defaultViewport: { width: 1280, height: 900 },
  });
  const page = await browser.newPage();
  page.setDefaultTimeout(30000);
  const consoleMsgs = [];
  page.on('console', m => { if (m.type() !== 'log') consoleMsgs.push(m.type() + ': ' + m.text()); });

  async function waitAI(ms) { await new Promise(r => setTimeout(r, ms)); }

  try {
    // 1) 登录
    await page.goto(BASE + '/login', { waitUntil: 'networkidle0' });
    await waitAI(1500);
    // 用「学生」演示账号 pill 自动填表
    const pill = await page.$$eval('.pill-account', els => {
      const e = els.find(x => (x.innerText || '').includes('student01'));
      if (e) { e.click(); return 'clicked'; }
      return null;
    });
    console.log('学生演示账号pill:', pill);
    if (!pill) {
      // 兜底手动填
      await page.type('input[placeholder="请输入账号"]', 'student01');
      await page.type('input[placeholder="请输入密码"]', '123456');
    }
    await waitAI(300);
    await page.click('.submit-btn');
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 20000 }).catch(() => {});
    await waitAI(2500);
    console.log('登录后 URL:', page.url());

    // 2) 进客服会话
    await page.goto(BASE + '/customer-service', { waitUntil: 'networkidle0' });
    await waitAI(2500);
    console.log('客服页 URL:', page.url());
    const hasNewSessionBtn = await page.$('.cs-new-session');
    console.log('「＋新建会话」按钮存在:', !!hasNewSessionBtn);

    // 3) 先点左侧第一个会话(川味小炒)激活输入区，再点「＋新建会话」
    await page.waitForSelector('.cs-session', { timeout: 15000 });
    const cnames = await page.$$eval('.cs-session', els => els.map(e => (e.innerText||'').slice(0,10)));
    console.log('左侧会话:', cnames);
    await page.click('.cs-session');
    await waitAI(1800);
    await page.waitForSelector('.cs-chat-input textarea', { timeout: 15000 });
    console.log('输入框已出现(会话已激活)');
    const hasNewSessionBtn2 = await page.$('.cs-new-session');
    if (hasNewSessionBtn2) {
      await page.click('.cs-new-session');
      await waitAI(2500);
      console.log('已点击「＋新建会话」');
    }

    // 4) 发一条「老顾客不满意」消息触发安抚
    const inputSel = 'textarea[placeholder="输入想咨询的问题，例如：今天的菜多久能出餐？"]';
    const msg = '老板我今天特别不顺，在你家吃了好多次了，等了好久菜还没好，心里真的不舒服。';
    await page.type(inputSel, msg);
    await page.click('.send-btn');
    console.log('已发送消息:', msg);

    // 5) 轮询等待 AI 回复(SenderType AI 的新气泡)
    let aiText = null;
    for (let i = 0; i < 40; i++) {
      await waitAI(1000);
      // 找消息气泡文本
      aiText = await page.evaluate(() => {
        // AI/商家回复 = .msg-row.msg-other 里的最后一条
        const rows = Array.from(document.querySelectorAll('.msg-row.msg-other'));
        if (rows.length === 0) return null;
        const last = rows[rows.length - 1];
        const t = (last.querySelector('.msg-content') ? last.querySelector('.msg-content').innerText : '').trim();
        return t || null;
      });
      if (aiText) break;
    }
    console.log('\n===== AI 回复出现 =====');
    console.log(aiText || '(未抓到)');
    console.log('=======================');

    // 6) 是否叫出学生名字(熟客记忆) & 是否发券
    console.log('AI 是否点名学生:', aiText ? /欣怡|student/i.test(aiText) : false);
    console.log('AI 是否提及给券台词:', aiText ? /免费|免单|打折|优惠|券/i.test(aiText) : false);

  } catch (e) {
    console.log('错误:', e.message);
  } finally {
    await browser.close();
  }
})();