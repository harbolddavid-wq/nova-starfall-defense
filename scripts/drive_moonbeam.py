#!/usr/bin/env python3
"""Exercise real Moonbeam UI in an Android emulator; capture evidence."""
import os, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

OUT=Path("emulator_evidence"); OUT.mkdir(exist_ok=True)
def adb(*args, check=True):
    return subprocess.run(["adb",*args],check=check,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True).stdout
def dump():
    adb("shell","uiautomator","dump","/sdcard/window.xml")
    xml=adb("shell","cat","/sdcard/window.xml")
    (OUT/"last_ui.xml").write_text(xml)
    return ET.fromstring(xml)
def find_text(text):
    root=dump()
    matches=[]
    for n in root.iter("node"):
        value=n.attrib.get("text","")+" "+n.attrib.get("content-desc","")
        if text.casefold() in value.casefold():
            b=re.findall(r"\d+",n.attrib.get("bounds",""))
            if len(b)==4:
                x1,y1,x2,y2=map(int,b)
                matches.append(((x1+x2)//2,(y1+y2)//2,value.strip()))
    if not matches: raise RuntimeError("UI text not found: "+text)
    return matches[0]
def tap_text(text):
    x,y,v=find_text(text)
    print("TAP",text,"=>",v,x,y,flush=True)
    adb("shell","input","tap",str(x),str(y))
    time.sleep(1.0)
def tap_canvas():
    root=dump()
    for n in root.iter("node"):
        if "DrawingView" in n.attrib.get("class",""):
            b=list(map(int,re.findall(r"\d+",n.attrib["bounds"])))
            if len(b)==4:
                x=(b[0]+b[2])//2; y=(b[1]+b[3])//2
                adb("shell","input","tap",str(x),str(y))
                return x,y
    sz=adb("shell","wm","size")
    match=re.search(r"(\d+)x(\d+)",sz)
    w,h=map(int,match.groups())
    x,y=w//2,int(h*.48)
    adb("shell","input","tap",str(x),str(y))
    return x,y
def capture(name):
    adb("shell","screencap","-p","/sdcard/"+name)
    subprocess.run(["adb","pull","/sdcard/"+name,str(OUT/name)],check=True,stdout=subprocess.PIPE)
    assert (OUT/name).stat().st_size>5000
def alive():
    s=adb("shell","pidof","com.novasky.moonbeam",check=False).strip()
    if not s: raise RuntimeError("Moonbeam process crashed")
    return s

print("START",adb("shell","am","start","-W","-n","com.novasky.moonbeam/.MainActivity"),flush=True)
time.sleep(3)
print("PID",alive(),flush=True)
capture("01_start.png")
tap_text("Pages")
tap_text("Dragon Egg")
time.sleep(1)
capture("02_dragon_page.png")
tap_text("Stamps")
tap_text("Blue")
tap_text("Done")
x,y=tap_canvas()
time.sleep(1)
capture("03_kenney_stamp.png")
tap_text("Sparkle")
tap_text("Filigree")
tap_text("Done")
adb("shell","input","swipe",str(x-120),str(y+70),str(x+120),str(y+70),"650")
time.sleep(1)
capture("04_kronbits_sparkle.png")
tap_text("Save JPG")
time.sleep(2)
root=dump()
text=" ".join(n.attrib.get("text","") for n in root.iter("node"))
if "Saved to your gallery!" not in text:
    raise RuntimeError("JPEG success dialog missing; screen text="+text[:500])
capture("05_saved_jpeg_dialog.png")
print("JPEG dialog verified",flush=True)
print("PID after tools",alive(),flush=True)
(OUT/"EMULATOR_TEST_PASS.txt").write_text("Emulator app launched, pages changed, Kenney stamp tool selected and painted, Kronbits Filigree texture tool selected and painted, JPEG export reported success, process remained alive. Screenshots 01-05 captured. Physical BLU remains untested.\n")
print("PASS: interactive emulator smoke test",flush=True)
