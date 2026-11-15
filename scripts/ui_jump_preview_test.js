const puppeteer=require('puppeteer-core');const CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe';const BASE='http://localhost:5180';const wait=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{const b=await puppeteer.launch({executablePath:CHROME,headless:'new',args:['--no-sandbox'],defaultViewport:{width:1440,height:950}});const p=await b.newPage();p.setDefaultTimeout(60000);const res=[];const ok=(n,c,d)=>{res.push(!!c);console.log((c?'PASS ':'FAIL ')+n+(d?'  '+d:''))};
await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(1200);
await p.$$eval('.pill-account',els=>els.find(x=>x.innerText.includes('100012'))?.click());await wait(300);await p.click('.submit-btn');await p.waitForNavigation({waitUntil:'networkidle2',timeout:20000}).catch(()=>{});await wait(2200);
await p.goto(BASE+'/merchant/ai-assistant',{waitUntil:'networkidle0'});await wait(2000);
const input=await p.$('.wz-upload input[type=file]');await input.uploadFile('D:/AI/AI Contest/canteen-feedback/gourmet food/10001.jpg');await wait(1800);
await p.click('.wz-input .ap-btn--brand');await p.waitForSelector('.wz-draft',{timeout:50000});await wait(500);
const hasJump=await p.evaluate(()=>!!document.querySelector('.wz-jump'));ok('草稿卡有「去档口预览」提示',hasJump);
await p.click('.wz-jump');await wait(2500);
ok('跳转到菜品管理',p.url().includes('/merchant/dishes'),p.url());
const active=await p.evaluate(()=>document.querySelector('.mode-switch .ap-pill.active')?.innerText.trim()||'');
ok('跳转后默认档口预览',active==='档口预览',active);
await p.evaluate(()=>localStorage.removeItem('canteen_dish_drafts'));await b.close();console.log(`\n==== ${res.filter(Boolean).length}/${res.length} passed ====`)})()
