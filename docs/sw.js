// વ્યૂહરચના – ઓફલાઇન કેશ (નવું વર્ઝન મૂકો ત્યારે v નંબર વધારવો)
const CACHE='vyuhrachna-v7';
const SHELL=['./','./index.html','./config.js','./manifest.json','./icon-192.png','./icon-512.png','./icon-maskable-512.png','./apple-touch-icon.png',
  './lib/pdf.min.js','./lib/pdf.worker.min.js','./lib/xlsx.full.min.js','./lib/mammoth.browser.min.js','./lib/html2canvas.min.js','./lib/jspdf.umd.min.js',
  './lib/firebase-app-compat.js','./lib/firebase-auth-compat.js','./lib/firebase-firestore-compat.js','./lib/leaflet.js','./lib/leaflet.css'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(SHELL)).then(()=>self.skipWaiting()));});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
const netFirst=req=>fetch(req).then(r=>{const cp=r.clone();caches.open(CACHE).then(c=>c.put(req,cp));return r;}).catch(()=>caches.match(req).then(h=>h||caches.match('./index.html')));
self.addEventListener('fetch',e=>{
  const req=e.request;if(req.method!=='GET')return;
  const url=new URL(req.url);
  if(url.pathname.endsWith('/version.json'))return; // અપડેટ ચેક — કદી કેશ નહીં
  if(/firestore|identitytoolkit|securetoken|googleapis\.com/.test(url.hostname))return; // લોગિન / ઓનલાઇન ડેટા કેશ નહીં
  if(req.mode==='navigate'||url.pathname.endsWith('/config.js')||url.pathname.endsWith('/index.html')){e.respondWith(netFirst(req));return;}
  e.respondWith(caches.match(req).then(hit=>hit||fetch(req).then(r=>{if(r.ok||r.type==='opaque'){const cp=r.clone();caches.open(CACHE).then(c=>c.put(req,cp));}return r;})));
});
self.addEventListener('notificationclick',e=>{e.notification.close();e.waitUntil(self.clients.matchAll({type:'window'}).then(cs=>{for(const c of cs){if('focus' in c)return c.focus();}return self.clients.openWindow('./index.html');}));});
