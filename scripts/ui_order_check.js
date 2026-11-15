const puppeteer=require('puppeteer-core');const CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe';const BASE='http://localhost:5180';const wait=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{const b=await puppeteer.launch({executablePath:CHROME,headless:'new',args:['--no-sandbox'],defaultViewport:{width:1440,height:950}});const p=await b.newPage();
await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(1200);
await p.$$eval('.pill-account',els=>els.find(x=>x.innerText.includes('100012'))?.click());await wait(300);await p.click('.submit-btn');await p.waitForNavigation({waitUntil:'networkidle2',timeout:20000}).catch(()=>{});await wait(2200);
await p.goto(BASE+'/merchant/dishes',{waitUntil:'networkidle0'});await wait(2500);
const pills=await p.$$eval('.mode-switch .ap-pill',els=>els.map(e=>e.innerText.trim()));
const active=await p.evaluate(()=>document.querySelector('.mode-switch .ap-pill.active')?.innerText.trim());
console.log('顺序:',JSON.stringify(pills),'| 默认:',active);
console.log(pills[0]==='档口预览'&&pills[1]==='菜品管理'&&active==='档口预览'?'PASS':'FAIL');
await b.close()})()
