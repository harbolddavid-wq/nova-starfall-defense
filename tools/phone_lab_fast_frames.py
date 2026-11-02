#!/usr/bin/env python3
"""Compact, bounded multi-frame evidence for Nova Phone Lab Live.

The underlying GitHub relay is public. Only use synthetic app fixtures.
A contact sheet replaces multiple round-trips for tap/swipe sequences.
"""
from __future__ import annotations
import hashlib
import io
import time
from PIL import Image, ImageDraw

MAX_ACTIONS=6
ALLOWED={"tap","swipe","back","home","settings","launch_fixture","wait"}

def collect(phone, action, perform):
    kind=action.get("action")
    if kind=="burst":
        n=int(action.get("frames",4))
        delay=int(action.get("interval_ms",150))
        if not (2<=n<=5 and 0<=delay<=400):
            raise ValueError("burst requires 2-5 frames, interval 0-400 ms")
        steps=[{"action":"wait","seconds":delay/1000} for _ in range(n)]
        title="burst"
    elif kind=="sequence":
        steps=action.get("steps")
        if not isinstance(steps,list) or not (1<=len(steps)<=MAX_ACTIONS):
            raise ValueError("sequence requires 1-6 steps")
        title="sequence"
        for s in steps:
            if not isinstance(s,dict) or s.get("action") not in ALLOWED:
                raise ValueError("sequence contains disallowed action")
            if s["action"]=="wait" and not (0<=float(s.get("seconds",.2))<=2.0):
                raise ValueError("wait must be 0-2 seconds")
    else: raise ValueError("unknown fast action")
    frames=[]
    start=time.monotonic()
    for index,step in enumerate(steps):
        if step["action"]=="wait":
            time.sleep(float(step.get("seconds",.2)))
            result={"action":"wait"}
        else:
            result=perform(step)
            time.sleep(.25)
        raw=phone.screen_png()
        frame=Image.open(io.BytesIO(raw)).convert("RGB")
        frames.append((frame,{
            "index":index,
            "action":step["action"],
            "sha256":hashlib.sha256(raw).hexdigest(),
            "elapsed_ms":round((time.monotonic()-start)*1000),
            "result":result,
        }))
    return title,frames

def contact_sheet(frames,max_base64_length=47000):
    if not (1<=len(frames)<=MAX_ACTIONS): raise ValueError("invalid frame count")
    for cell_width,quality in ((200,49),(175,42),(155,36),(135,30)):
        cols=2 if len(frames)>1 else 1
        rows=(len(frames)+cols-1)//cols
        image_w=cell_width*cols
        sample=frames[0][0]
        cell_h=round(cell_width*sample.height/sample.width)
        out=Image.new("RGB",(image_w,rows*(cell_h+22)),"#181a24")
        pen=ImageDraw.Draw(out)
        for k,(frame,meta) in enumerate(frames):
            x=(k%cols)*cell_width
            y=(k//cols)*(cell_h+22)
            pen.text((x+5,y+4),f"{k+1}: {meta['action']} +{meta['elapsed_ms']}ms",fill="white")
            thumb=frame.resize((cell_width,cell_h),Image.Resampling.LANCZOS)
            out.paste(thumb,(x,y+22))
        buf=io.BytesIO()
        out.save(buf,format="JPEG",quality=quality,optimize=True)
        data=buf.getvalue()
        if (len(data)+2)//3*4 < max_base64_length:
            return data,image_w,out.height,cell_width
    raise ValueError("Cannot fit contact sheet inside GitHub comment size")
