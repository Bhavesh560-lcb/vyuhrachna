# નવું વર્ઝન બહાર પાડવા: python3 tools/new_version.py "ફેરફાર ૧" "ફેરફાર ૨"
# docs/index.html નો APP_VERSION અને docs/version.json નો version બંને +1 કરે છે
import json,os,re,sys
R=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
H=os.path.join(R,'docs','index.html');V=os.path.join(R,'docs','version.json')
v=json.load(open(V,encoding='utf-8'));n=int(v.get('version',1))+1
s=open(H,encoding='utf-8').read();s=re.sub(r'const APP_VERSION=\d+;','const APP_VERSION=%d;'%n,s,count=1)
open(H,'w',encoding='utf-8').write(s)
v['version']=n
if len(sys.argv)>1:v['notes']=sys.argv[1:]
json.dump(v,open(V,'w',encoding='utf-8'),ensure_ascii=False,indent=2)
print('નવું વર્ઝન: v%d — હવે docs/index.html અને docs/version.json બંને GitHub માં અપલોડ કરો'%n)
