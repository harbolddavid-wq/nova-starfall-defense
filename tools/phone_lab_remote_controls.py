#!/usr/bin/env python3
"""Nova Phone Lab proof: controlled ADB actions and verifiable screenshots.

This script intentionally does not expose an arbitrary shell or network port.
It runs only on a disposable emulator with synthetic/system UI content.
"""
import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path

OUT = Path("phone-lab-live-evidence")
OUT.mkdir(parents=True, exist_ok=True)
ALLOWED = {"screenshot", "settings", "tap", "swipe", "back", "home", "wait", "ui_dump"}
manifest = []
def adb(*args, check=True, **kwargs):
    return subprocess.run(["adb", *map(str,args)], check=check, **kwargs)
def screenshot(label):
    dest = OUT / f"{len(manifest):02d}-{label}.png"
    with dest.open("wb") as handle:
        adb("exec-out", "screencap", "-p", stdout=handle)
    data = dest.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Emulator did not return a valid PNG: {label}")
    return dest, hashlib.sha256(data).hexdigest()
def dump_ui(label):
    adb("shell","uiautomator","dump","/sdcard/nova-lab-ui.xml", check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    xml = adb("exec-out","cat","/sdcard/nova-lab-ui.xml",check=False,capture_output=True).stdout
    if xml.startswith(b"<?xml"):
        (OUT / f"{len(manifest):02d}-{label}.xml").write_bytes(xml)
def main():
    commands=json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    if not isinstance(commands,list) or len(commands)>30: raise ValueError("Expected up to 30 JSON commands")
    adb("wait-for-device")
    sdk=adb("shell","getprop","ro.build.version.sdk",capture_output=True,text=True).stdout.strip()
    if sdk!="36": raise RuntimeError(f"Expected Android 16/API36, found {sdk}")
    adb("shell","wm","size","720x1600")
    adb("shell","wm","density","260")
    adb("shell","settings","put","secure","immersive_mode_confirmations","confirmed",check=False)
    time.sleep(2)
    for i,c in enumerate(commands):
        action=c.get("action"); label=c.get("label",f"step-{i:02d}")
        if action not in ALLOWED or not label.replace("-","").replace("_","").isalnum():
            raise ValueError(f"Disallowed command or label at {i}: {action!r}")
        start=time.monotonic()
        if action=="settings": adb("shell","am","start","-a","android.settings.SETTINGS")
        elif action=="tap":
            x,y=int(c["x"]),int(c["y"])
            if not (0<=x<720 and 0<=y<1600):raise ValueError("tap outside display")
            adb("shell","input","tap",x,y)
        elif action=="swipe":
            x1,y1,x2,y2,duration=(int(c[k]) for k in ("x1","y1","x2","y2","duration_ms"))
            if any(v<0 or v>=limit for v,limit in [(x1,720),(x2,720),(y1,1600),(y2,1600)]) or not 100<=duration<=3000:
                raise ValueError("swipe outside display or invalid duration")
            adb("shell","input","swipe",x1,y1,x2,y2,duration)
        elif action=="back": adb("shell","input","keyevent","KEYCODE_BACK")
        elif action=="home": adb("shell","input","keyevent","KEYCODE_HOME")
        elif action=="wait": time.sleep(min(5,max(0,float(c.get("seconds",1)))))
        elif action=="ui_dump": dump_ui(label)
        time.sleep(1.2 if action!="wait" else 0.1)
        shot,sha=screenshot(label)
        manifest.append({"index":i,"action":action,"label":label,"screen_file":shot.name,
                         "sha256":sha,"seconds":round(time.monotonic()-start,2)})
        print(f"STEP {i}: {action} -> {shot.name} ({sha[:12]})",flush=True)
    (OUT/"manifest.json").write_text(json.dumps({"sdk":sdk,"screen":"720x1600","steps":manifest},indent=2))
    distinct=len(set(s["sha256"] for s in manifest))
    (OUT/"RESULT.txt").write_text(f"Executed {len(manifest)} actions. Captured {len(manifest)} verified PNGs with {distinct} distinct SHA-256 hashes. Review screenshots to verify actual UI states. No live stream in this first proof.\n")
    if distinct<2: raise RuntimeError("No visual change across commands; inspect screenshots")
    print(f"PASS: {len(manifest)} actions, {distinct} unique screenshots",flush=True)
if __name__=="__main__":
    try: main()
    except Exception as e:
        print(f"ERROR: {e}",file=sys.stderr)
        raise
