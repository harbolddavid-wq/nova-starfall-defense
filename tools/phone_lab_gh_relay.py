#!/usr/bin/env python3
"""GitHub issue comment-based, short-lived Android emulator command bridge.

This is intentionally restricted to a disposable Android Settings test screen.
Public GitHub issue comments are NOT appropriate for private APKs or user data.
No public HTTP listener, shell evaluation, tunnels, or long-lived credentials.
"""
import base64, hashlib, io, json, os, re, sys, time
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from PIL import Image

sys.path.insert(0,os.path.dirname(__file__))
from phone_lab_live_core import PhoneLab

REPO=os.environ["GITHUB_REPOSITORY"]
ISSUE=int(os.environ.get("PHONE_LAB_ISSUE","2"))
RUN_ID=os.environ.get("GITHUB_RUN_ID","unknown")
SESSION=os.environ.get("PHONE_LAB_SESSION",f"lab-{RUN_ID}")
OWNER=os.environ.get("PHONE_LAB_OWNER","harbolddavid-wq")
TOKEN=os.environ["GITHUB_TOKEN"]
PHONE=PhoneLab()
URL=f"https://api.github.com/repos/{REPO}/issues/{ISSUE}/comments"
TIMEOUT_SECONDS=int(os.environ.get("PHONE_LAB_SESSION_SECONDS","270"))

def api(method,url,payload=None):
    data=None if payload is None else json.dumps(payload,ensure_ascii=True).encode("utf-8")
    req=Request(url,data=data,headers={
        "Authorization":f"Bearer {TOKEN}",
        "Accept":"application/vnd.github+json",
        "X-GitHub-Api-Version":"2022-11-28",
        "User-Agent":"NovaPhoneLab-disposable-test",
        "Content-Type":"application/json",
    },method=method)
    try:
        with urlopen(req,timeout=18) as response:
            return json.load(response)
    except HTTPError as e:
        detail=e.read()[:800]
        raise RuntimeError(f"GitHub {method} {url} returned {e.code}: {detail!r}")

def post(kind,**payload):
    txt=kind+":"+json.dumps({"session":SESSION,"run_id":RUN_ID,**payload},separators=(",",":"),ensure_ascii=True)
    if len(txt)>63000:raise ValueError(f"Response too long {len(txt)}")
    api("POST",URL,{"body":txt})

def screenshot_jpeg():
    raw=PHONE.screen_png()
    original_hash=hashlib.sha256(raw).hexdigest()
    for width in (540,460,380,320):
        source=Image.open(io.BytesIO(raw)).convert("RGB")
        height=round(source.height*width/source.width)
        source=source.resize((width,height),Image.Resampling.LANCZOS)
        result=io.BytesIO()
        source.save(result,format="JPEG",quality=46,optimize=True)
        jpeg=result.getvalue()
        if len(base64.b64encode(jpeg))<49000:
            return jpeg,original_hash,width,height
    raise ValueError("Screenshot exceeds comment limit")

def perform(action):
    cmd=action.get("action")
    if cmd=="status": return PHONE.status()
    if cmd=="launch_fixture":
        PHONE.adb("shell","am","start","-n","com.novasky.phonelab.fixture/.MainActivity")
        return {"action":"launch_fixture","package":"com.novasky.phonelab.fixture","status":"command_sent"}
    if cmd=="settings": return PHONE.settings()
    if cmd=="tap":return PHONE.tap(int(action["x"]),int(action["y"]))
    if cmd=="swipe":return PHONE.swipe(int(action["x1"]),int(action["y1"]),int(action["x2"]),int(action["y2"]),int(action.get("duration_ms",450)))
    if cmd=="back":return PHONE.back()
    if cmd=="home":return PHONE.home()
    if cmd=="screenshot":return {"action":"screenshot"}
    if cmd=="stop":return {"action":"stop"}
    raise ValueError("Unsupported action")

def main():
    sdk=PHONE.status()
    if sdk.get("api_level")!="36":raise RuntimeError("Expected Android 16")
    PHONE.adb("shell","wm","size","720x1600")
    PHONE.adb("shell","wm","density","260")
    PHONE.adb("shell","settings","put","secure","immersive_mode_confirmations","confirmed")
    apk="lab-fixture/app/build/outputs/apk/debug/app-debug.apk"
    if not os.path.isfile(apk): raise RuntimeError("Synthetic APK not built")
    print("INSTALLING FIXTURE",apk,flush=True)
    print(PHONE.adb("install","-r",apk,timeout=90).decode("utf-8","replace"),flush=True)
    package=PHONE.adb("shell","pm","path","com.novasky.phonelab.fixture").decode("utf-8","replace").strip()
    if not package.startswith("package:"):raise RuntimeError("Fixture APK installation not confirmed")
    PHONE.adb("shell","am","start","-n","com.novasky.phonelab.fixture/.MainActivity")
    time.sleep(3)
    print("SESSION READY",sdk,"FIXTURE",package,flush=True)
    post("LAB_READY",device=sdk,commands=["status","screenshot","launch_fixture","settings","tap","swipe","back","home","stop"],
         installed_package="com.novasky.phonelab.fixture",
         expires_in_seconds=TIMEOUT_SECONDS,warning="Synthetic APK only; issue comments are public")
    processed=set()
    finish=time.monotonic()+TIMEOUT_SECONDS
    while time.monotonic()<finish:
        comments=api("GET",URL+"?per_page=100")
        for c in comments:
            cid=c.get("id")
            if cid in processed:continue
            processed.add(cid)
            user=(c.get("user") or {}).get("login")
            body=c.get("body") or ""
            if user.lower()!=OWNER.lower() or not body.startswith("LAB_CMD:"):
                continue
            try:
                command=json.loads(body[len("LAB_CMD:"):])
                if command.get("session")!=SESSION:continue
                seq=command.get("seq","unspecified")
                result=perform(command)
                time.sleep(.9)
                jpeg,raw_hash,w,h=screenshot_jpeg()
                b64=base64.b64encode(jpeg).decode("ascii")
                post("LAB_RESULT",command_comment_id=cid,seq=seq,action=command["action"],
                     result=result,image_base64=b64,image_mime="image/jpeg",
                     width=w,height=h,raw_png_sha256=raw_hash)
                print("EXECUTED",cid,command["action"],seq,raw_hash[:12],flush=True)
                if command["action"]=="stop":
                    print("SESSION CLOSED",flush=True)
                    return
            except Exception as e:
                print(f"COMMAND ERROR {cid}: {e}",file=sys.stderr,flush=True)
                post("LAB_ERROR",command_comment_id=cid,error=str(e)[:220])
        time.sleep(4)
    post("LAB_ENDED",reason="disposable session timed out")
    print("SESSION TIMED OUT",flush=True)

if __name__=="__main__":
    main()
