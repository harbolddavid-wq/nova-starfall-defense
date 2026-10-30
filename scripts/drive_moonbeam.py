#!/usr/bin/env python3
"""Moonbeam 0.1.2 emulator interaction: genuine taps, screenshots and JPEG checks.
Pinch multitouch is NOT simulated by adb input; only +/- zoom and Fit are runtime-tested."""
from pathlib import Path
import subprocess,re,time,xml.etree.ElementTree as ET,hashlib
OUT=Path("emulator_evidence");OUT.mkdir(exist_ok=True)
PKG="com.novasky.moonbeam.gallerypreview";ACT="com.novasky.moonbeam.MainActivity"
log=[]
def run(*args,timeout=90,check=True,binary=False):
    p=subprocess.run(["adb",*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
    if check and p.returncode:raise RuntimeError(f"adb {args}: {p.stderr.decode(errors='replace')[:600]}")
    return p.stdout if binary else p.stdout.decode(errors="replace")
def evidence(s):
    log.append(s);print(s,flush=True)
def capture(name):
    try:
        data=run("exec-out","screencap","-p",timeout=40,binary=True)
        if not data.startswith(b"\x89PNG") or len(data)<5000:raise RuntimeError("No PNG data")
        (OUT/name).write_bytes(data)
        evidence(f"SCREENSHOT {name}: {len(data)} bytes sha256={hashlib.sha256(data).hexdigest()}")
        return True
    except Exception as e:
        evidence("SCREENSHOT FAILURE "+name+": "+str(e));return False
def dump():
    last=None
    for i in range(8):
        try:
            run("shell","uiautomator","dump","/data/local/tmp/moonbeam_ui.xml",timeout=55)
            raw=run("shell","cat","/data/local/tmp/moonbeam_ui.xml",timeout=25)
            root=ET.fromstring(raw[raw.index("<?xml"):])
            # Cold boot may show a Pixel Launcher ANR over Moonbeam.
            # Dismiss only the system ANR; never dismiss an app error.
            if any("isn't responding" in n.attrib.get("text","") and n.attrib.get("package","")=="android" for n in root.iter("node")):
                wait=next((n for n in root.iter("node") if n.attrib.get("text","")=="Close app"),None)
                if wait is not None:
                    xy=bounds(wait)
                    if xy:
                        run("shell","input","tap",str(xy[0]),str(xy[1]))
                        evidence("CLOSED hung Pixel Launcher after system ANR")
                        time.sleep(3)
                        continue
            (OUT/"last_ui.xml").write_text(raw)
            return root
        except Exception as e:last=e;time.sleep(2)
    raise RuntimeError("UI dump failed: "+str(last))
def bounds(n):
    v=list(map(int,re.findall(r"\d+",n.attrib.get("bounds",""))))
    return ((v[0]+v[2])//2,(v[1]+v[3])//2) if len(v)==4 else None
def find(label):
    root=dump()
    for n in root.iter("node"):
        if label.lower() in (n.attrib.get("text","")+" "+n.attrib.get("content-desc","")).lower():
            xy=bounds(n)
            if xy:return n,xy
    return None
def size():
    m=re.search(r"(\d+)x(\d+)",run("shell","wm","size"))
    return int(m.group(1)),int(m.group(2))
def tap(label,scroll=False):
    found=find(label)
    if not found and scroll:
        w,h=size()
        # Scroll only the horizontal tools row; never accidentally paint on canvas.
        for direction in (-1,1):
            for _ in range(8):
                x1,x2=(.83,.17) if direction==-1 else (.17,.83)
                run("shell","input","swipe",str(int(w*x1)),str(int(h*.805)),str(int(w*x2)),str(int(h*.805)),"350")
                time.sleep(.5)
                found=find(label)
                if found:break
            if found:break
    if not found:raise RuntimeError("UI label not found: "+label)
    n,(x,y)=found
    if n.attrib.get("enabled")=="false":raise RuntimeError("Control disabled: "+label)
    run("shell","input","tap",str(x),str(y));time.sleep(.9)
    evidence(f"TAP {label} ({x},{y})")
def alive(stage):
    pid=run("shell","pidof",PKG,check=False).strip()
    state=run("shell","dumpsys","activity","activities",check=False)
    if not pid or PKG not in state:raise RuntimeError("App lost foreground at "+stage)
    evidence("ALIVE "+stage+" pid="+pid)
def paint(stage,x=.35,y=.47):
    w,h=size()
    for attempt in range(3):
        dump()  # close any system ANR before touching Moonbeam
        run("shell","input","swipe",str(int(w*x)),str(int(h*y)),str(int(w*(x+.12))),str(int(h*(y+.035))),"400")
        time.sleep(.8)
        dump()
        state=find("Undo")
        if state and state[0].attrib.get("enabled")=="true":break
        evidence(f"RETRY paint {stage}: no enabled Undo after swipe, attempt {attempt+1}")
    alive(stage)
def history(stage):
    paint(stage)
    tap("Undo");alive(stage+" Undo")
    tap("Redo");alive(stage+" Redo")
def page(category,title):
    tap("Pages");tap(category);tap(title);alive("page "+title)
try:
    run("logcat","-c",check=False)
    evidence("START "+run("shell","am","start","-n",PKG+"/"+ACT))
    time.sleep(7);dump();alive("launch");capture("01_launch.png")
    history("brush");capture("02_brush_history.png")
    tap("Fill",scroll=True);history("fill")
    tap("Stamps",scroll=True);tap("Blue");tap("Done");history("Kenney stamp")
    tap("Sparkle",scroll=True);tap("Filigree");tap("Done");history("Kronbits sparkle")
    page("Night Creatures","Wolf at the Chapel");capture("03_wolf_page.png")
    page("Arcane Relics","Crystal Seer");capture("04_crystal_page.png")
    page("Moonlit Oddities","A Ghost's Tea Party");capture("05_ghost_tea_page.png")
    page("Illustrated Extras","The Dragon's Last Stand");capture("05b_cc0_dragon.png")
    tap("Zoom +",scroll=True);alive("zoom plus");capture("06_zoomed.png")
    tap("Fit");alive("fit reset");capture("07_fit.png")
    tap("Magic");tap("Rainbow Trail");history("rainbow");capture("08_rainbow.png")
    tap("Magic");tap("Ghostlight");history("ghostlight");capture("09_ghostlight.png")
    tap("Magic");tap("Mirror Magic");history("mirror ghostlight");capture("10_mirror.png")
    tap("Brush",scroll=True)
    tap("Save JPG");time.sleep(1)
    root=dump();ui=" ".join(n.attrib.get("text","") for n in root.iter("node"))
    if "Saved to your gallery!" not in ui:raise RuntimeError("JPEG confirmation not present: "+ui[:450])
    capture("11_jpeg_success.png");alive("JPEG export")
    media=run("shell","content","query","--uri","content://media/external/images/media","--projection","_display_name",check=False)
    (OUT/"media_store_query.txt").write_text(media)
    if "Moonbeam_" not in media:evidence("MEDIASTORE row not exposed to shell; JPEG dialog confirmed")
    else:evidence("MEDIASTORE JPEG row found")
    runtime=run("logcat","-d","-s","AndroidRuntime:E",check=False)
    (OUT/"android_runtime_errors.txt").write_text(runtime)
    if "FATAL EXCEPTION" in runtime and PKG in runtime:raise RuntimeError("Fatal exception in logcat")
    (OUT/"EMULATOR_TEST_PASS.txt").write_text("PASS: actual emulator UI interactions for brush, fill, Kenney stamps, Kronbits sparkles, page categories and 4 gallery pages including new CC0 dragon, zoom+ and Fit, Rainbow, Ghostlight, Mirror, Undo/Redo, JPEG success dialog, foreground stability. Pinch/two-finger pan and physical BLU are NOT verified by adb single-pointer testing.\n")
    evidence("PASS emulator interactions")
except Exception as e:
    (OUT/"EMULATOR_TEST_FAILURE.txt").write_text(str(e)+"\n")
    capture("99_failure.png")
    raise
finally:
    (OUT/"driver_trace.txt").write_text("\n".join(log)+"\n")
