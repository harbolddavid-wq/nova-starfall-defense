#!/usr/bin/env python3
"""UI regression test. Never confuse an emulator pass with David's BLU test."""
import os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
OUT=Path("emulator_evidence");OUT.mkdir(exist_ok=True)
PKG="com.novasky.moonbeam.repair"
ACT="com.novasky.moonbeam.MainActivity"
def adb(*args,check=True,timeout=90):
    p=subprocess.run(["adb",*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout)
    if check and p.returncode:raise RuntimeError(f"adb {args}: {p.stdout.decode(errors='replace')[:600]}")
    return p.stdout.decode(errors="replace")
def capture(name):
    # GitHub's headless emulator sometimes fails screencap even when the app is alive.
    # Screenshots are evidence when available, not a prerequisite for UI behavior tests.
    for i in range(2):
        try:
            adb("shell","screencap","-p","/sdcard/"+name,timeout=30)
            adb("pull","/sdcard/"+name,str(OUT/name),timeout=30)
            if (OUT/name).stat().st_size>1000:return True
        except Exception as e:print("SCREENSHOT UNAVAILABLE:",str(e)[:160],flush=True)
        time.sleep(2)
    return False
def dump():
    last=None
    for i in range(4):
        try:
            adb("shell","uiautomator","dump","/sdcard/moonbeam_ui.xml",timeout=50)
            raw=adb("shell","cat","/sdcard/moonbeam_ui.xml")
            root=ET.fromstring(raw[raw.index("<?xml"):])
            (OUT/"last_ui.xml").write_text(raw)
            return root
        except Exception as e:last=e;time.sleep(3)
    raise RuntimeError("UI dump unavailable: "+str(last))
def node_for(label):
    root=dump()
    for n in root.iter("node"):
        if label.lower() in (n.attrib.get("text","")+" "+n.attrib.get("content-desc","")).lower():
            vals=list(map(int,re.findall(r"\d+",n.attrib.get("bounds",""))))
            if len(vals)==4:return n,(vals[0]+vals[2])//2,(vals[1]+vals[3])//2
    raise RuntimeError("Button not found: "+label)
def tap(label):
    n,x,y=node_for(label)
    print("TAP",label,"enabled",n.attrib.get("enabled"),x,y,flush=True)
    adb("shell","input","tap",str(x),str(y));time.sleep(1)
def check_alive(stage):
    pid=adb("shell","pidof",PKG,check=False).strip()
    activity=adb("shell","dumpsys","activity","activities",check=False)
    if not pid:raise RuntimeError("Process crashed after "+stage)
    if PKG not in activity:raise RuntimeError("App no longer in foreground after "+stage)
    print("ALIVE",stage,pid,flush=True)
def tap_canvas():
    w,h=map(int,re.search(r"(\d+)x(\d+)",adb("shell","wm","size")).groups())
    x,y=w//2,int(h*.46)
    adb("shell","input","swipe",str(x-90),str(y),str(x+90),str(y+100),"400")
    time.sleep(1)
def test_history(stage):
    tap_canvas()
    tap("Undo")
    check_alive(stage+" undo")
    tap("Redo")
    check_alive(stage+" redo")
    tap("Undo")
    check_alive(stage+" second undo")
    tap("Redo")
    check_alive(stage+" second redo")
adb("logcat","-c",check=False)
print("START",adb("shell","am","start","-n",PKG+"/"+ACT,timeout=70),flush=True)
time.sleep(8)
check_alive("launch")
capture("01_launch.png")
test_history("brush")
capture("02_brush_undo_redo.png")
tap("Pages");tap("The Bone Crown")
check_alive("gothic page")
capture("03_bone_crown.png")
tap("Fill")
test_history("fill")
tap("Stamps");tap("Blue");tap("Done")
test_history("Kenney stamp")
tap("Sparkle");tap("Filigree");tap("Done")
test_history("Kronbits sparkle")
capture("04_tools_after_history.png")
tap("Save JPG")
time.sleep(2)
root=dump()
ui=" ".join(n.attrib.get("text","") for n in root.iter("node"))
if "Saved to your gallery!" not in ui:raise RuntimeError("JPEG success dialog missing: "+ui[:350])
capture("05_jpeg_success.png")
check_alive("JPEG export")
logs=adb("logcat","-d","-s","AndroidRuntime:E",check=False)
(OUT/"android_runtime_errors.txt").write_text(logs)
if "FATAL EXCEPTION" in logs and PKG in logs:raise RuntimeError("AndroidRuntime fatal exception in test")
(OUT/"EMULATOR_TEST_PASS.txt").write_text("Emulator UI PASS: brush, fill, Kenney stamps, Kronbits sparkle each Undo/Redo twice; page switch; foreground process stays alive; JPEG success dialog. BLU phone not tested. Screenshots optional on headless runner.\n")
print("PASS: Moonbeam repair interactive regression test",flush=True)
