const puppeteer=require('puppeteer-core');const CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe';const BASE='http://localhost:5180';const wait=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{const b=await puppeteer.launch({executablePath:CHROME,headless:'new',args:['--no-sandbox'],defaultViewport:{width:1440,height:1000}});const p=await b.newPage();p.setDefaultTimeout(90000);
await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(1200);
await p.$$eval('.pill-account',els=>els.find(x=>x.innerText.includes('100012'))?.click());await wait(300);await p.click('.submit-btn');await p.waitForNavigation({waitUntil:'networkidle2',timeout:20000}).catch(()=>{});await wait(2200);
await p.goto(BASE+'/merchant/ai-assistant',{waitUntil:'networkidle0'});await wait(1800);
await p.$$eval('.skill-chip',els=>els.find(b=>b.innerText.includes('销售分析'))?.click());await wait(600);
await p.$$eval('.sales-panel .head-actions button',els=>els.find(b=>b.innerText.includes('让 AI 分析'))?.click());await p.waitForSelector('.summary-card',{timeout:90000});await wait(1200);
await p.evaluate(()=>document.querySelector('.table-card')?.scrollIntoView({block:'center'}));await wait(600);
await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/sales-skill-table.png'});await b.close();console.log('done')})()
