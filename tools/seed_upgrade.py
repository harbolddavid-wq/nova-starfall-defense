#!/usr/bin/env python3
"""Seed one real Starwell move in the prior APK using observed Android UI bounds."""
import pathlib,re,subprocess,time,xml.etree.ElementTree as ET

def adb(*args):return subprocess.check_output(['adb',*args],text=True)
def tree():
 adb('shell','uiautomator','dump','/sdcard/holodeck-window.xml')
 data=adb('shell','cat','/sdcard/holodeck-window.xml');pathlib.Path('evidence/upgrade-before.xml').write_text(data);return ET.fromstring(data)
def find(fragment,clickable=False):
 for _ in range(6):
  root=tree()
  for n in root.iter('node'):
   if clickable and n.get('clickable')!='true':continue
   if fragment.lower() in (n.get('text','')+' '+n.get('content-desc','')).lower():
    b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
    if len(b)==4 and b[2]>b[0] and b[3]>b[1]:return b
  time.sleep(.5)
 raise RuntimeError('Prior app control not found: '+fragment)
def tap(fragment):
 l,t,r,b=find(fragment,True);adb('shell','input','tap',str((l+r)//2),str((t+b)//2));time.sleep(1)
adb('shell','am','start','-W','-n','com.novasky.holodeck/.MainActivity');time.sleep(1)
tap('Open the portal');tap('Wake the sky');l,t,r,b=find('Carry light from the bright well')
w,h=r-l,b-t
adb('shell','input','swipe',str(round(l+w*.5)),str(round(t+h*.87)),str(round(l+w*.3)),str(round(t+h*.24)),'650')
find('1 / 5 stars awake');adb('shell','am','force-stop','com.novasky.holodeck')
print('PASS: prior APK played through a real first-star drag and saved it before the host update.')
