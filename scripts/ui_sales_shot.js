const puppeteer=require('puppeteer-core');const CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe';const BASE='http://localhost:5180';const wait=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{const b=await puppeteer.launch({executablePath:CHROME,headless:'new',args:['--no-sandbox'],defaultViewport:{width:1440,height:1000}});const p=await b.newPage();p.setDefaultTimeout(90000);
await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await p.evaluate(()=>localStorage.clear());await p.goto(BASE+'/login',{waitUntil:'networkidle0'});await wait(1200);
await p.$$eval('.pill-account',els=>els.find(x=>x.innerText.includes('100012'))?.click());await wait(300);await p.click('.submit-btn');await p.waitForNavigation({waitUntil:'networkidle2',timeout:20000}).catch(()=>{});await wait(2200);
await p.goto(BASE+'/merchant/sales-analysis',{waitUntil:'networkidle0'});await p.waitForSelector('.summary-card',{timeout:90000});await wait(1500);
await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/sales-analysis-final.png',fullPage:false});
await p.evaluate(()=>{document.querySelector('.table-card')?.scrollIntoView({block:'start'})});await wait(700);
await p.screenshot({path:'D:/AI/AI Contest/canteen-feedback/screenshots/sales-table-final.png',fullPage:false});
await b.close();console.log('done')})()
