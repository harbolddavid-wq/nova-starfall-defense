#!/usr/bin/env python3
"""Cross-internet HTTPS/WSS test with actual Android 16 and synthetic fixture.

Only intended for isolated GitHub CI with disposable tokens and test fixture.
Cloudflare Quick Tunnel is an ephemeral transport, NOT a persistent private host.
Public hostname still requires relay's two separate bearer credentials.
No screenshots, credentials, or complete tunnel hostname go into reports.
"""
import hashlib
import json
import os
import re
import secrets
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib import error, request

BRIDGE = Path(__file__).resolve().parents[1] / "phone_lab_private"
sys.path.insert(0, str(BRIDGE))
from controller import PrivatePhoneLab
from phone_lab_live_core import PhoneLab
from real_android_smoke import (expect, find_center, png_info, text_exists,
                                wait, xml_tree, authenticated_bad_request)

HERE = Path(__file__).resolve().parent
REPORT = HERE / "remote_tls_report.json"
FIXTURE = "com.novasky.phonelab.fixture/.MainActivity"
TUNNEL_RE = re.compile(r"https://([a-z0-9-]+)\.trycloudflare\.com(?:/|\s|$)", re.I)


def parse_tunnel_url(log: str) -> str | None:
    match = TUNNEL_RE.search(log)
    return "https://" + match.group(1) + ".trycloudflare.com" if match else None


def remote_health(url: str) -> bool:
    with request.urlopen(url + "/health", timeout=8) as response:
        return json.load(response).get("status") == "ok"


def await_tunnel(logpath: Path, process, timeout=85) -> str:
    end = time.monotonic() + timeout
    candidate = None
    while time.monotonic() < end:
        if process.poll() is not None:
            raise RuntimeError("Tunnel exited before a ready connection")
        if logpath.exists():
            candidate = parse_tunnel_url(logpath.read_text(errors="replace"))
            if candidate:
                try:
                    if remote_health(candidate):
                        return candidate
                except (OSError, error.URLError, TimeoutError, ValueError):
                    pass
        time.sleep(1)
    raise RuntimeError("Temporary tunnel did not become reachable over HTTPS")


def wait_safe(predicate, description, seconds=50):
    end = time.monotonic()+seconds
    last = None
    while time.monotonic()<end:
        try:
            found=predicate()
            if found:
                return found
        except Exception as ex:
            last=type(ex).__name__
        time.sleep(0.8)
    raise AssertionError("Timeout waiting for "+description+ (" (last: "+last+")" if last else ""))


def main():
    phone=PhoneLab()
    expect(phone.status()["api_level"] == "36", "Android 16 API 36 required")
    expect(phone.adb("shell","pm","path","com.novasky.phonelab.fixture").startswith(b"package:"), "Fixture absent")
    phone.adb("shell","am","start","-n",FIXTURE)
    wait(lambda: text_exists(xml_tree(phone),"BUTTON PRESSES: 0"),"fixture initial counter",seconds=35)
    agent_key,control_key=secrets.token_urlsafe(40),secrets.token_urlsafe(40)
    env=dict(os.environ,PHONE_LAB_AGENT_TOKEN=agent_key,PHONE_LAB_CONTROL_TOKEN=control_key)
    procs=[]
    try:
        with (HERE/"_remote_relay.log").open("w") as rl:
            relay=subprocess.Popen([sys.executable,str(BRIDGE/"relay.py")],env=env,cwd=HERE,stdout=rl,stderr=subprocess.STDOUT)
        procs.append(relay)
        wait_safe(lambda: remote_health("http://127.0.0.1:8765"),"loopback relay",seconds=20)
        cf_bin=os.environ.get("PHONE_LAB_CLOUDFLARED_BIN", "cloudflared")
        with (HERE/"_cloudflared.log").open("w") as tl:
            tunnel=subprocess.Popen([cf_bin,"tunnel","--no-autoupdate","--url","http://127.0.0.1:8765"],env=os.environ,cwd=HERE,stdout=tl,stderr=subprocess.STDOUT)
        procs.append(tunnel)
        base=await_tunnel(HERE/"_cloudflared.log",tunnel)
        expect(base.startswith("https://") and base.endswith(".trycloudflare.com"),"Not public TLS")
        # Test the public endpoint directly; no auth means no access to controls.
        expect(authenticated_bad_request(base+"/v1/sessions"),"Unauthenticated remote sessions accessible")
        env_agent=dict(env,PHONE_LAB_RELAY_WSS_URL=base.replace("https://","wss://",1)+"/v1/agent")
        with (HERE/"_remote_agent.log").open("w") as al:
            agent=subprocess.Popen([sys.executable,str(BRIDGE/"agent.py")],env=env_agent,cwd=HERE,stdout=al,stderr=subprocess.STDOUT)
        procs.append(agent)
        client=PrivatePhoneLab(base_url=base,token=control_key)
        sessions=wait_safe(lambda:client.sessions().get("sessions"),"agent's *external WSS* session",seconds=65)
        expect(len(sessions)==1,"Expected one agent")
        sid=sessions[0]["session_id"]
        status=client.command(sid,"status")["result"]
        expect(status["api_level"]=="36","Not real Android 16")
        before=png_info(client.command(sid,"screenshot"))
        x,y=find_center(xml_tree(phone),"PRESS ME +1")
        client.command(sid,"tap",x=x,y=y)
        wait(lambda:text_exists(xml_tree(phone),"BUTTON PRESSES: 1"),"tap actually changed Android fixture",seconds=18)
        after=png_info(client.command(sid,"screenshot"))
        expect(before["sha256"]!=after["sha256"],"Screenshots identical after tap")
        client.command(sid,"swipe",x1=550,y1=1200,x2=550,y2=380,duration_ms=450)
        swipe=png_info(client.command(sid,"screenshot"))
        expect(after["sha256"]!=swipe["sha256"],"Swipe had no visual result")
        client.command(sid,"settings")
        wait(lambda:text_exists(xml_tree(phone),"Settings"),"system Settings",seconds=20)
        client.command(sid,"back")
        client.command(sid,"home")
        home=png_info(client.command(sid,"screenshot"))
        expect(home["sha256"]!=after["sha256"],"Home unchanged")
        report={
            "result":"PASS","tested_at_utc":datetime.now(timezone.utc).isoformat(),
            "github_run_id":os.environ.get("GITHUB_RUN_ID","local"),
            "api_level":status["api_level"],"dimensions":status["screen"],
            "transport":"real external HTTPS controller plus external WSS outbound Android 16 agent via ephemeral Cloudflare Quick Tunnel",
            "tunnel_type":"anonymous temporary tunnel, hostname intentionally omitted",
            "authenticated":True,"separate_agent_and_controller_tokens":True,
            "checks":["external TLS HTTPS health","unauthorized remote request rejected","real Android 16 agent over external WSS",
                      "external authenticated Android status","real screenshot PNG via external HTTPS","tap changed actual Android APK counter",
                      "screenshot hash changed after tap","swipe changed actual screenshot","Settings opened","Back/Home navigation"],
            "png_metadata":{"before":before,"after_tap":after,"after_swipe":swipe,"home":home},
            "limit":"This is temporary proof, no standing hostname, no remote MCP registered, no private apps tested."
        }
        REPORT.write_text(json.dumps(report,indent=2)+"\n")
        print("PASS: real Android 16 external HTTPS/WSS tunnel; authenticated commands and PNGs, tap/swipe/settings/back/home")
        print("Evidence:", REPORT, "API",status["api_level"],"dimensions",status["screen"])
    finally:
        for proc in reversed(procs):
            if proc.poll() is None:
                proc.terminate()
                try:proc.wait(timeout=5)
                except subprocess.TimeoutExpired: proc.kill();proc.wait(timeout=5)
        # Runtime logs may include public temporary hostname; keep them local, never as CI artifacts.

if __name__=="__main__":
    main()