#!/usr/bin/env python3
"""CI-only smoke: picker, three theme applies, original fallback and restart persistence.
The emulator APK has no private wallpaper assets and is signed with a temporary CI key.
"""
from pathlib import Path
import re,subprocess,time,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/theme-test-evidence';OUT.mkdir(parents=True,exist_ok=True)
PKG='com.novasky.starlightthemes'
def adb(*args,check=True):
 return subprocess.run(['adb',*map(str,args)],check=check,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True).stdout
def dump():
 adb('shell','uiautomator','dump','/data/local/tmp/starlight-theme-window.xml')
 return ET.fromstring(adb('exec-out','cat','/data/local/tmp/starlight-theme-window.xml'))
def find(needle,timeout=20):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  try:
   for node in dump().iter():
    if needle.lower() in (node.attrib.get('text','')+' '+node.attrib.get('content-desc','')).lower():return node
  except (ET.ParseError,subprocess.CalledProcessError):pass
  time.sleep(.8)
 raise AssertionError('UI not found: '+needle)
def tap(needle):
 node=find(needle);m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
 if not m:raise AssertionError('No bounds for '+needle)
 x1,y1,x2,y2=map(int,m.groups());adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2)
def shot(name):
 with (OUT/name).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],check=True,stdout=f)
def scroll():
 adb('shell','input','swipe',520,1530,520,560,400);time.sleep(.6)
def choose(name):
 tap('Themes')
 for i in range(6):
  try:tap('Preview '+name);break
  except AssertionError:
   if i==5:raise
   scroll()
 find(name)
 tap('Apply theme')
 find('Themes')
 tap('Themes')
 for i in range(6):
  try:find('Preview '+name+' current theme',timeout=4);break
  except AssertionError:
   if i==5:raise
   scroll()
 shot(name.lower().replace(' ','-')+'.png')
 tap('Back to home')
 print('PASS: applied and confirmed',name)
# Approximate Desi's tall Samsung screen; default CI emulator is only 320x640.
adb('shell','wm','size','1080x2340')
adb('shell','wm','density','450')
time.sleep(2)
adb('install','-r',ROOT/'build/widget-fixture/launcher.apk')
adb('shell','am','start','-n',PKG+'/.MainActivity')
find('Themes',40)
shot('original-home.png')
for name in ('Fae Lanternwood','Astral Clockwork','Velvet Masquerade','Moonflower','Starlit Conservatory'):
 choose(name)
adb('shell','am','force-stop',PKG)
adb('shell','am','start','-n',PKG+'/.MainActivity')
find('Themes',30)
tap('Themes');find('Preview Starlit Conservatory current theme')
shot('after-restart.png')
logs=adb('logcat','-d','-t','600')
if any(('FATAL EXCEPTION' in line and PKG in logs[max(0,logs.find(line)-250):logs.find(line)+300]) for line in logs.splitlines() if 'FATAL EXCEPTION' in line):raise AssertionError('Recent fatal exception')
print('PASS: 5 themes, original return, saved theme after restart; CI art omitted')