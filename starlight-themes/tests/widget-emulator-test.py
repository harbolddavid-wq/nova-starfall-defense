#!/usr/bin/env python3
"""Exercise real widget binding/configuration and persisted home state on AOSP."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
build=root/"build/widget-fixture"
evidence=root/"build/widget-test-evidence"
evidence.mkdir(parents=True,exist_ok=True)

def adb(*args,check=True):
    return subprocess.run(["adb",*map(str,args)],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=check).stdout

def hierarchy():
    adb("shell","uiautomator","dump","/sdcard/starlight-window.xml")
    raw=adb("exec-out","cat","/sdcard/starlight-window.xml")
    return ET.fromstring(raw)

def match(node, text, partial=False):
    values=(node.attrib.get("text",""),node.attrib.get("content-desc",""))
    wanted=text.casefold()
    return any((wanted in value.casefold() if partial else wanted==value.casefold()) for value in values)

def wait_for(text,partial=False,timeout=30):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        try:
            tree=hierarchy()
            for node in tree.iter():
                if match(node,text,partial):
                    return node
        except (ET.ParseError,subprocess.CalledProcessError):
            pass
        time.sleep(1)
    raise AssertionError("Timed out waiting for "+repr(text))

def tap_node(node):
    bounds=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",node.attrib["bounds"])
    if not bounds: raise AssertionError("No bounds for target")
    left,top,right,bottom=map(int,bounds.groups())
    adb("shell","input","tap",(left+right)//2,(top+bottom)//2)

def tap(text,partial=False):
    tap_node(wait_for(text,partial))

def find_scrolling(text,partial=False):
    previous=None
    for _ in range(10):
        tree=hierarchy()
        for node in tree.iter():
            if match(node,text,partial):
                return node
        lists=[node for node in tree.iter() if node.attrib.get("scrollable")=="true"]
        if not lists: break
        visible=tuple(node.attrib.get("text","") for node in lists[0].iter() if node.attrib.get("text"))
        if visible==previous: break
        previous=visible
        bounds=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",lists[0].attrib["bounds"])
        left,top,right,bottom=map(int,bounds.groups())
        middle=(left+right)//2
        adb("shell","input","swipe",middle,bottom-100,middle,top+100,450)
        time.sleep(1)
    raise AssertionError("Could not find "+repr(text)+" in the scrollable widget list")

def tap_scrolling(text,partial=False):
    tap_node(find_scrolling(text,partial))

def menu():
    tap("Home options")
    tap("Widgets")

def choose_fixture():
    menu()
    tap("Add a widget")
    tap("Music & audio")
    tap_scrolling("Starlight QA Widget, 4 widgets, show choices")
    tap_scrolling("Add Starlight QA Widget choice 1 of 4")
    end=time.monotonic()+30
    while time.monotonic()<end:
        tree=hierarchy()
        if any(match(n,"Starlight QA widget configuration") for n in tree.iter()):
            return
        for candidate in ("Allow","ALLOW","Create","CREATE"):
            if any(match(n,candidate) for n in tree.iter()):
                tap(candidate)
                break
        time.sleep(1)
    raise AssertionError("Widget bind/configuration screen did not appear")

def verify_picker():
    menu()
    tap("Add a widget")
    tap("Music & audio")
    tap("Search apps or widgets")
    adb("shell","input","text","music")
    adb("shell","input","keyevent","4")
    tap_scrolling("Starlight QA Widget, 4 widgets, show choices")
    wait_for("Choice 1 of 4 · 2 × 2 cells on Home · Music & audio")
    wait_for("A sample music control with a generated preview.")
    wait_for("Preview provided by Starlight QA Widget")
    with (evidence/"widget-picker-generated.png").open("wb") as f:
        subprocess.run(["adb","exec-out","screencap","-p"],stdout=f,check=True)
    find_scrolling("No preview supplied by this app")
    find_scrolling("A different music control with a layout preview.")
    with (evidence/"widget-picker-layout.png").open("wb") as f:
        subprocess.run(["adb","exec-out","screencap","-p"],stdout=f,check=True)
    find_scrolling("A wide music control with an image preview.")
    find_scrolling("Add Starlight QA Widget choice 4 of 4")
    tap("Close")
    print("PASS: grouped audio choices, descriptions, sizes, search and missing-preview fallback")

def edit_fixture(action):
    menu()
    tap("Move or resize a widget")
    tap("Starlight QA Widget · page",partial=True)
    tap(action)

def require_absent(text):
    tree=hierarchy()
    if any(match(node,text) for node in tree.iter()):
        raise AssertionError("Unexpected UI text remains: "+text)

def bounds(node):
    found=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",node.attrib["bounds"])
    if not found: raise AssertionError("Missing target bounds")
    return tuple(map(int,found.groups()))

def choose_two_favorites(existing):
    tap("Choose your favorite apps")
    tree=hierarchy()
    options=[node for node in tree.iter() if node.attrib.get("class","").endswith("CheckedTextView")
             and node.attrib.get("text") and node.attrib.get("text") not in existing]
    if len(options)<2: raise AssertionError("Not enough apps visible in favorites chooser")
    names=[options[0].attrib["text"],options[1].attrib["text"]]
    for name in names: tap(name)
    tap("Set favorites")
    for name in names: wait_for(name)
    return names

def drag_down_one_cell(node,width_cells,height_cells):
    left,top,right,bottom=bounds(node)
    w=(right-left)//width_cells;h=(bottom-top)//height_cells
    start_x=left+w//2;start_y=top+h//2
    # Slow initial movement stays within Android's long-press touch slop.
    adb("shell","input","swipe",start_x,start_y,start_x,start_y+h,9000)
    return top,h

def wait_moved(label,old_top,cell_height):
    end=time.monotonic()+20
    while time.monotonic()<end:
        current=bounds(wait_for(label))[1]
        if current!=old_top:
            if abs(current-(old_top+cell_height))>max(32,cell_height//6):
                raise AssertionError(label+" moved to the wrong row")
            return current
        time.sleep(1)
    raise AssertionError(label+" did not move from its original position")

try:
    adb("shell","wm","size","1080x2340")
    adb("shell","wm","density","450")
    adb("shell","settings","put","system","font_scale","1.15")
    adb("shell","input","keyevent","82")
    adb("logcat","-c")
    adb("install","-r",build/"fixture.apk")
    adb("install","-r",build/"launcher.apk")
    adb("shell","am","broadcast","-n","com.novasky.widgetfixture/.FixtureWidget","-a","com.novasky.widgetfixture.SET_PREVIEW")
    (evidence/"providers.txt").write_text(adb("shell","dumpsys","appwidget"))
    adb("shell","am","start","-n","com.novasky.starlightthemes/.MainActivity")
    wait_for("Home options")
    wait_for("Choose your favorite apps")
    time.sleep(2)
    initial=hierarchy()
    existing={node.attrib.get("content-desc","") for node in initial.iter() if node.attrib.get("content-desc")}
    favorites=choose_two_favorites(existing)
    tap("Home options")
    tap("Undo favorite changes")
    wait_for("Choose your favorite apps")
    for name in favorites: require_absent(name)
    tap("Choose your favorite apps")
    for name in favorites: tap(name)
    tap("Set favorites")
    icon=wait_for(favorites[0]);old_top,cell_height=drag_down_one_cell(icon,1,1)
    new_top=wait_moved(favorites[0],old_top,cell_height)
    adb("shell","am","force-stop","com.novasky.starlightthemes")
    adb("shell","am","start","-n","com.novasky.starlightthemes/.MainActivity")
    if abs(bounds(wait_for(favorites[0]))[1]-new_top)>5: raise AssertionError("Dragged app position was not restored")
    print("PASS: choose and undo favorites, drag an app directly and restore its position")

    a=bounds(wait_for(favorites[0]));b=bounds(wait_for(favorites[1]))
    adb("shell","input","swipe",(a[0]+a[2])//2,(a[1]+a[3])//2,(b[0]+b[2])//2,(b[1]+b[3])//2,14000)
    tap("Folder, folder")
    wait_for("Hold a book for more options")
    for name in favorites: wait_for(name)
    with (evidence/"library-folder.png").open("wb") as f:
        subprocess.run(["adb","exec-out","screencap","-p"],stdout=f,check=True)
    adb("shell","input","keyevent","4")
    print("PASS: create and open the library-style folder with both apps")

    verify_picker()
    choose_fixture()
    tap("Finish setup")
    wait_for("Starlight QA widget ready")
    print("PASS: add, bind, configure and render a real RemoteViews widget")

    tap("Home options")
    tap("Add a home page")
    edit_fixture("Move to a position")
    tap("Page 2",partial=True)
    tap("Row 1, column 1, available")
    widget=wait_for("Starlight QA widget ready")
    old_top,cell_height=drag_down_one_cell(widget,2,2)
    wait_moved("Starlight QA widget ready",old_top,cell_height)
    print("PASS: drag a widget directly")

    edit_fixture("Resize in place")
    tap("3 × 2 cells")
    wait_for("Starlight QA widget ready")
    menu()
    tap("Move or resize a widget")
    wait_for("Starlight QA Widget · page 2 · 3 × 2")
    adb("shell","input","keyevent","4")
    adb("shell","am","force-stop","com.novasky.starlightthemes")
    adb("shell","am","start","-n","com.novasky.starlightthemes/.MainActivity")
    wait_for("Starlight QA widget ready")
    menu()
    tap("Move or resize a widget")
    wait_for("Starlight QA Widget · page 2 · 3 × 2")
    adb("shell","input","keyevent","4")
    print("PASS: move to another page, resize, force-stop and restore")

    menu()
    tap("Remove a widget")
    tap("Starlight QA Widget · page 2")
    tap("Remove")
    wait_for("Home options")
    require_absent("Starlight QA widget ready")
    print("PASS: remove widget")

    choose_fixture()
    tap("Cancel setup")
    wait_for("Home options")
    require_absent("Starlight QA widget ready")
    choose_fixture()
    adb("shell","am","force-stop","com.novasky.starlightthemes")
    adb("shell","am","force-stop","com.novasky.widgetfixture")
    adb("shell","am","start","-n","com.novasky.starlightthemes/.MainActivity")
    wait_for("Home options")
    menu()
    tap("Continue unfinished setup")
    wait_for("Starlight QA widget configuration")
    tap("Finish setup")
    wait_for("Starlight QA widget ready")
    print("PASS: cancelled configuration and recovery after interrupted setup")

    logs=adb("logcat","-d","-t","3000")
    (evidence/"logcat.txt").write_text(logs)
    lines=logs.splitlines()
    for index,line in enumerate(lines):
        if "FATAL EXCEPTION" in line and any("Process: com.novasky.starlightthemes" in nearby for nearby in lines[index:index+8]):
            raise AssertionError("Launcher crash detected in logcat")
    print("PASS: no launcher fatal exception in recent logcat")
except Exception:
    try:
        (evidence/"window.xml").write_text(adb("exec-out","cat","/sdcard/starlight-window.xml",check=False))
        (evidence/"logcat.txt").write_text(adb("logcat","-d","-t","3000",check=False))
        with (evidence/"screen.png").open("wb") as f:
            subprocess.run(["adb","exec-out","screencap","-p"],stdout=f,check=False)
    finally:
        raise
