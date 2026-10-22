# docs/ (વેબ એપ) માંથી Android એપની અંદરની ફાઇલો બનાવે છે — GitHub Actions આપમેળે ચલાવે છે
import os,shutil
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D=os.path.join(ROOT,'docs');W=os.path.join(ROOT,'app','src','main','assets','www')
if os.path.isdir(W):shutil.rmtree(W)
os.makedirs(os.path.join(W,'lib'))
s=open(os.path.join(D,'index.html'),encoding='utf-8').read().replace('<link rel="manifest" href="manifest.json">','')
open(os.path.join(W,'index.html'),'w',encoding='utf-8').write(s)
for f in ['config.js','version.json','icon-192.png','icon-512.png','icon-maskable-512.png','apple-touch-icon.png']:
    shutil.copy(os.path.join(D,f),os.path.join(W,f))
for f in os.listdir(os.path.join(D,'lib')):
    shutil.copy(os.path.join(D,'lib',f),os.path.join(W,'lib',f))
print('web -> android: ok')
