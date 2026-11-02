#!/usr/bin/env python3
"""Real Android 16 end-to-end smoke through the existing private relay.

Launches real relay + outbound ADB agent as separate subprocesses, drives the
synthetic Android fixture through authenticated HTTP/WebSocket commands, and
saves only non-sensitive, content-free test metadata. The relay is loopback-only.
"""
import hashlib
import json
import os
import re
import secrets
import struct
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib import request, error
from xml.etree import ElementTree

from controller import PrivatePhoneLab
from phone_lab_live_core import PhoneLab

HERE = Path(__file__).resolve().parent
REPORT = Path(os.environ.get("PHONE_LAB_REAL_REPORT", str(HERE / "real_android_report.json")))
FIXTURE = "com.novasky.phonelab.fixture/.MainActivity"


def expect(cond, msg):
    if not cond:
        raise AssertionError(msg)


def png_info(blob):
    expect(blob.startswith(b"\x89PNG\r\n\x1a\n") and len(blob) > 64, "No real PNG screenshot")
    expect(blob[12:16] == b"IHDR", "Invalid PNG IHDR")
    w, h = struct.unpack(">II", blob[16:24])
    expect(200 <= w <= 4000 and 300 <= h <= 5000, "Implausible screen size")
    return {"sha256": hashlib.sha256(blob).hexdigest(), "width": w, "height": h, "bytes": len(blob)}


def xml_tree(phone):
    # Core PhoneLab intentionally exposes only allowlisted actions, not UI dumps.
    # This test-only observer uses Android uiautomator to verify what a real APK rendered.
    phone.adb("shell", "uiautomator", "dump", "/sdcard/nova-phone-lab-proof.xml", timeout=30)
    xml = phone.adb("exec-out", "cat", "/sdcard/nova-phone-lab-proof.xml", timeout=15)
    return ElementTree.fromstring(xml.decode("utf-8", "replace"))


def text_exists(tree, value):
    return any(value in el.attrib.get("text", "") for el in tree.iter())


def find_center(tree, label):
    for el in tree.iter():
        if el.attrib.get("text", "").strip() == label:
            b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", el.attrib.get("bounds", ""))
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                expect(x2 > x1 and y2 > y1, "Invalid button bounds")
                return ((x1+x2)//2, (y1+y2)//2)
    raise AssertionError("Real fixture button not found: " + label)


def wait(predicate, desc, seconds=25, interval=.5):
    end = time.monotonic() + seconds
    last = None
    while time.monotonic() < end:
        try:
            result = predicate()
            if result:
                return result
        except (OSError, ConnectionError, error.URLError, ValueError) as exc:
            last = exc
        time.sleep(interval)
    raise AssertionError("Timed out waiting for " + desc + (": " + str(last) if last else ""))


def authenticated_bad_request(url):
    try:
        request.urlopen(request.Request(url, headers={"Authorization": "Bearer invalid"}), timeout=4)
    except error.HTTPError as exc:
        return exc.code == 401
    return False


def main():
    phone = PhoneLab()
    observed = phone.status()
    expect(observed["api_level"] == "36", "Expected a *real* Android 16 / API 36 emulator")
    package = phone.adb("shell", "pm", "path", "com.novasky.phonelab.fixture").decode().strip()
    expect(package.startswith("package:"), "Synthetic fixture APK must be installed first")
    phone.adb("shell", "am", "start", "-n", FIXTURE)
    wait(lambda: text_exists(xml_tree(phone), "BUTTON PRESSES: 0"), "installed fixture to draw", seconds=30)
    base = "http://127.0.0.1:8765"
    agent_key, control_key = secrets.token_urlsafe(40), secrets.token_urlsafe(40)
    common = dict(os.environ)
    common.update(PHONE_LAB_AGENT_TOKEN=agent_key, PHONE_LAB_CONTROL_TOKEN=control_key)
    env_agent = dict(common, PHONE_LAB_RELAY_WSS_URL="ws://127.0.0.1:8765/v1/agent")
    relay_log = HERE / "_relay_runtime.log"
    agent_log = HERE / "_agent_runtime.log"
    relay = agent = None
    try:
        with relay_log.open("w") as rl, agent_log.open("w") as al:
            relay = subprocess.Popen([sys.executable, str(HERE / "relay.py")], cwd=HERE, env=common, stdout=rl, stderr=subprocess.STDOUT)
            wait(lambda: json.load(request.urlopen(base + "/health", timeout=2)).get("status") == "ok", "private relay boot", seconds=20)
            expect(authenticated_bad_request(base + "/v1/sessions"), "Unauthenticated client unexpectedly got session list")
            agent = subprocess.Popen([sys.executable, str(HERE / "agent.py")], cwd=HERE, env=env_agent, stdout=al, stderr=subprocess.STDOUT)
            client = PrivatePhoneLab(base_url=base, token=control_key)
            sessions = wait(lambda: client.sessions().get("sessions"), "outbound real ADB agent", seconds=35)
            expect(len(sessions) == 1, "One single-use emulator session expected")
            sid = sessions[0]["session_id"]
            status = client.command(sid, "status")["result"]
            expect(status["api_level"] == "36", "ADB status did not round trip")
            before = png_info(client.command(sid, "screenshot"))
            pos = find_center(xml_tree(phone), "PRESS ME +1")
            tap = client.command(sid, "tap", x=pos[0], y=pos[1])
            expect(tap["result"].get("status") == "command_sent", "Relay did not send tap")
            wait(lambda: text_exists(xml_tree(phone), "BUTTON PRESSES: 1"), "real Android button response", seconds=15)
            after = png_info(client.command(sid, "screenshot"))
            expect(before["sha256"] != after["sha256"], "Before/after screenshots identical despite tap")
            client.command(sid, "swipe", x1=550, y1=1200, x2=550, y2=380, duration_ms=450)
            swipe_frame = png_info(client.command(sid, "screenshot"))
            expect(after["sha256"] != swipe_frame["sha256"], "Swipe did not change visible screen")
            client.command(sid, "settings")
            wait(lambda: "com.android.settings" in phone.adb("shell", "dumpsys", "window", "windows").decode("utf-8", "replace") or text_exists(xml_tree(phone), "Settings"), "Android Settings open", seconds=18)
            client.command(sid, "back")
            client.command(sid, "home")
            home = png_info(client.command(sid, "screenshot"))
            expect(home["sha256"] != after["sha256"], "Home did not change the screen")
            output = {
                "status": "PASS", "verified_at_utc": datetime.now(timezone.utc).isoformat(),
                "github_run_id": os.environ.get("GITHUB_RUN_ID", "local"), "emulator_api": status["api_level"],
                "emulator_dimensions": status["screen"], "transport": "localhost HTTP/WebSocket with separate authenticated agent and controller",
                "session_id_sha256_prefix": hashlib.sha256(sid.encode()).hexdigest()[:12],
                "checks": ["real Android 16 API 36", "fixture installed & launched", "unauthorized controller denied",
                           "outbound agent registered", "authenticated status", "real PNG screenshot",
                           "touch changed APK counter 0 to 1", "new screenshot differs after touch",
                           "swipe changed screen", "Settings action", "Back/Home action"],
                "screenshots": {"initial": before, "after_tap": after, "after_swipe": swipe_frame, "home": home},
                "note": "Screenshot bytes used only inside runner; NO screenshot uploaded to public logs/artifacts. No private APKs."
            }
            REPORT.parent.mkdir(parents=True, exist_ok=True)
            REPORT.write_text(json.dumps(output, indent=2) + "\n")
            print("PASS: real Android 16 private relay, screenshot, tap with verified counter, swipe, settings, back, home")
            print("Evidence:", REPORT, "Android API", status["api_level"], "size", status["screen"])
    finally:
        for proc in (agent, relay):
            if proc is not None and proc.poll() is None:
                proc.terminate()
                try: proc.wait(timeout=5)
                except subprocess.TimeoutExpired: proc.kill(); proc.wait(timeout=5)


if __name__ == "__main__":
    main()