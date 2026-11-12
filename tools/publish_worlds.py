#!/usr/bin/env python3
"""Versioned content publishing. Does not build or modify the host APK."""
import argparse,base64,hashlib,json,os,pathlib,re,subprocess,tempfile,time,zipfile
ROOT=pathlib.Path(__file__).resolve().parent.parent
REPO='harbolddavid-wq/nova-starfall-defense'
TAG='holodeck-worlds'
def canonical(obj):return json.dumps(obj,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()
def sha(data):return hashlib.sha256(data).hexdigest()
def gh(*args,check=True):return subprocess.run(['gh',*args],check=check,text=True,capture_output=True)
def pack(folder,out,override=None):
 config=json.loads((folder/'world.json').read_text());paths=config.pop('files')
 if override:config.update(override)
 assert re.fullmatch('[a-z][a-z0-9-]{1,47}',config['id'])
 assert config['runtime']==1 and config['versionCode']>0
 manifest=dict(config);manifest['files']={}
 bodies={}
 for name in paths:
  assert re.fullmatch('[A-Za-z0-9_./-]+',name) and all(p not in ('','..','.') for p in name.split('/')) and name!='experience.json'
  data=(folder/name).read_bytes();assert len(data)<=8*1024*1024
  bodies[name]=data;manifest['files'][name]={'bytes':len(data),'sha256':sha(data)}
 assert config['entry'] in bodies and len(bodies)<=128 and sum(map(len,bodies.values()))<32*1024*1024
 bodies['experience.json']=canonical(manifest)
 name=f"{config['id']}-{config['versionCode']}.zip";p=out/name
 with zipfile.ZipFile(p,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for key,data in sorted(bodies.items()):
   info=zipfile.ZipInfo(key,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
 data=p.read_bytes();assert len(data)<=16*1024*1024
 entry=dict(config,bytes=len(data),sha256=sha(data),url=f'https://github.com/{REPO}/releases/download/{TAG}/{name}')
 return entry,p

def sign(body,key,out):
 payload=canonical(body);raw=out/'payload.json';raw.write_bytes(payload);sig=out/'signature.b64'
 subprocess.run(['java',str(ROOT/'tools/CatalogSigner.java'),str(key),str(raw),str(sig)],check=True,capture_output=True)
 envelope={'algorithm':'SHA256withRSA','payload':base64.b64encode(payload).decode(),'signature':sig.read_text()}
 return canonical(envelope)

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--keystore',default='.signing/candidate.keystore');parser.add_argument('--out',default='build/world-release');parser.add_argument('--upload',action='store_true');parser.add_argument('--fixtures',action='store_true');args=parser.parse_args()
 out=ROOT/args.out;out.mkdir(parents=True,exist_ok=True);key=ROOT/args.keystore
 assert key.is_file(),'Original publisher/app signer is required'
 entries=[];packages=[]
 for folder in sorted((ROOT/'experiences').iterdir()):
  if not (folder/'world.json').is_file():continue
  entry,p=pack(folder,out);entries.append(entry);packages.append(p)
 assert entries
 body={'schema':1,'publisher':'nova-pocket-holodeck','runtime':1,'minHostVersion':4,'revision':time.time_ns()//1000,'experiences':entries}
 envelope=sign(body,key,out);(out/'catalog.json').write_bytes(envelope)
 if args.fixtures:
  fixture=ROOT/'app/src/androidTest/assets/delivery';fixture.mkdir(parents=True,exist_ok=True)
  folder=ROOT/'experiences/lanternwake';entry,p=pack(folder,out,{'versionCode':json.loads((folder/'world.json').read_text())['versionCode']+1,'version':'1.0.1-test','subtitle':'The harbor remembers'})
  (fixture/'lanternwake-v2.zip').write_bytes(p.read_bytes())
  updated=dict(body,revision=body['revision']+1,experiences=[entry]);(fixture/'catalog-v2.json').write_bytes(sign(updated,key,out))
 if args.upload:
  found=gh('release','view',TAG,'-R',REPO,'--json','assets',check=False)
  if found.returncode:
   gh('release','create',TAG,'-R',REPO,'--title','Pocket Holodeck — World Catalog','--notes','Signed portable experiences for the installed Android host. Experience packages are versioned separately from the APK.')
   assets=[]
  else:assets=json.loads(found.stdout)['assets']
  # Immutable versions: changing content requires a versionCode bump.
  for p in packages:
   existing=next((a for a in assets if a['name']==p.name),None)
   if existing:
    with tempfile.TemporaryDirectory() as tmp:
     gh('release','download',TAG,'-R',REPO,'--pattern',p.name,'--dir',tmp)
     if sha((pathlib.Path(tmp)/p.name).read_bytes())!=sha(p.read_bytes()):raise SystemExit(f'Package {p.name} already exists with different bytes; increment versionCode.')
   else:gh('release','upload',TAG,str(p),'-R',REPO)
  gh('release','upload',TAG,str(out/'catalog.json'),'-R',REPO,'--clobber')
 print(json.dumps({'worlds':[e['id'] for e in entries],'revision':body['revision'],'catalog':str(out/'catalog.json'),'uploaded':args.upload}))
if __name__=='__main__':main()
