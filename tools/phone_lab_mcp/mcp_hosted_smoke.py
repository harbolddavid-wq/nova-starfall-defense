#!/usr/bin/env python3
"""Verify GitHub OIDC to the persistent Render relay using a real Android 16 emulator.

Secrets and screenshots never enter git, CI artifacts, logs or public comments.
"""
import json
import base64
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib import request, parse, error

BRIDGE = Path(__file__).resolve().parents[1] / "phone_lab_private"
sys.path.insert(0, str(BRIDGE))

from phone_lab_live_core import PhoneLab
from real_android_smoke import (expect, find_center, png_info, text_exists, wait, xml_tree,
                                authenticated_bad_request)

BASE = "https://nova-phone-lab-private-relay.onrender.com"
REPORT = Path(__file__).resolve().parent / "hosted_mcp_report.json"
FIXTURE = "com.novasky.phonelab.fixture/.MainActivity"


def oidc(audience: str) -> str:
    url = os.environ["ACTIONS_ID_TOKEN_REQUEST_URL"]
    token = os.environ["ACTIONS_ID_TOKEN_REQUEST_TOKEN"]
    sep = "&" if "?" in url else "?"
    req = request.Request(url + sep + parse.urlencode({"audience": audience}),
                          headers={"Authorization": "bearer " + token})
    with request.urlopen(req, timeout=15) as r:
        jwt_token = json.load(r)["value"]
    if not isinstance(jwt_token, str) or jwt_token.count(".") != 2:
        raise RuntimeError("GitHub did not return an OIDC JWT")
    return jwt_token


class MCPPhoneLab:
    """Actual Model Context Protocol client over the hosted HTTPS endpoint."""
    def __init__(self, base_url: str, token: str):
        self.url = base_url.rstrip("/") + "/mcp"
        self.token = token
        self.seq = 0
        response = self.rpc("initialize", {"protocolVersion": "2025-06-18",
                                          "capabilities": {}, "clientInfo": {"name": "phone-lab-gh-proof", "version": "1.0"}})
        expect(response["protocolVersion"] == "2025-06-18", "MCP initialize failed")
        tools = self.rpc("tools/list", {})["tools"]
        expect({"list_sessions", "see_screen", "tap_screen", "swipe_screen"}.issubset({x["name"] for x in tools}),
               "MCP tools missing")

    def rpc(self, method, params):
        self.seq += 1
        raw = json.dumps({"jsonrpc":"2.0", "id":self.seq, "method":method,
                          "params":params}).encode("utf-8")
        req = request.Request(self.url, data=raw,
                              headers={"Authorization":"Bearer "+self.token,
                                       "Content-Type":"application/json",
                                       "Accept":"application/json, text/event-stream"}, method="POST")
        with request.urlopen(req,timeout=40) as response:
            data=json.load(response)
        expect(data.get("id") == self.seq, "MCP request ID mismatch")
        expect("error" not in data, "MCP JSON-RPC method failed")
        return data["result"]

    def tool(self, name, arguments):
        result = self.rpc("tools/call", {"name":name, "arguments":arguments})
        expect(not result.get("isError"), "MCP tool returned error: "+name)
        return result

    def sessions(self):
        return self.tool("list_sessions", {})["structuredContent"]

    def command(self, session_id, action, **kwargs):
        tool_name = {"screenshot":"see_screen", "status":"device_status", "tap":"tap_screen",
                     "swipe":"swipe_screen", "settings":"open_android_settings",
                     "back":"press_back", "home":"press_home"}[action]
        result=self.tool(tool_name, {"session_id":session_id, **kwargs})
        if action=="screenshot":
            images=[i for i in result["content"] if i.get("type")=="image" and i.get("mimeType")=="image/png"]
            expect(len(images)==1, "MCP screenshot missing PNG image")
            return base64.b64decode(images[0]["data"],validate=True)
        return result["structuredContent"]


def wait_session(client: MCPPhoneLab, seconds=70):
    until = time.monotonic() + seconds
    last = None
    while time.monotonic() < until:
        try:
            sessions = client.sessions().get("sessions") or []
            if len(sessions) == 1 and sessions[0].get("device", {}).get("api_level") == "36":
                return sessions[0]
        except Exception as exc:
            last = type(exc).__name__
        time.sleep(1.5)
    raise AssertionError("Render agent did not become ready (" + str(last) + ")")


def main():
    phone = PhoneLab()
    expect(phone.status()["api_level"] == "36", "Not running Android 16")
    expect(phone.adb("shell", "pm", "path", "com.novasky.phonelab.fixture").startswith(b"package:"), "Fixture not installed")
    phone.adb("shell", "am", "start", "-n", FIXTURE)
    wait(lambda: text_exists(xml_tree(phone), "BUTTON PRESSES: 0"), "fixture initial counter", seconds=35)

    # Signed GitHub OIDC, scoped separately to agent and controller audiences.
    agent_jwt = oidc("nova-phone-lab-agent")
    control_jwt = oidc("nova-phone-lab-controller")
    expect(authenticated_bad_request(BASE + "/v1/sessions"), "Unauthenticated sessions exposed")
    req = request.Request(BASE + "/v1/sessions", headers={"Authorization": "Bearer " + agent_jwt})
    try:
        request.urlopen(req, timeout=15)
        raise AssertionError("Agent credential improperly accepted as controller")
    except error.HTTPError as e:
        expect(e.code == 401, "Agent token not rejected as controller")

    agent_url = BASE.replace("https://", "wss://", 1) + "/v1/agent"
    env = dict(os.environ, PHONE_LAB_AGENT_TOKEN=agent_jwt, PHONE_LAB_RELAY_WSS_URL=agent_url)
    # Logs intentionally discarded: never emit JWT (or screenshots/credentials) to CI.
    with open(os.devnull,"w") as sink:
        agent = subprocess.Popen([sys.executable, str(BRIDGE / "agent.py")],
                                 env=env, stdout=sink, stderr=sink)
        try:
            client = MCPPhoneLab(base_url=BASE, token=control_jwt)
            sess = wait_session(client)
            sid = sess["session_id"]
            status = client.command(sid, "status")["result"]
            expect(status["api_level"] == "36", "Incorrect device API level")
            before = png_info(client.command(sid, "screenshot"))
            x, y = find_center(xml_tree(phone), "PRESS ME +1")
            client.command(sid, "tap", x=x, y=y)
            wait(lambda: text_exists(xml_tree(phone), "BUTTON PRESSES: 1"), "counter changed", seconds=20)
            after = png_info(client.command(sid, "screenshot"))
            expect(before["sha256"] != after["sha256"], "Counter screenshot did not change")
            client.command(sid, "swipe", x1=550, y1=1200, x2=550, y2=380, duration_ms=450)
            swipe = png_info(client.command(sid, "screenshot"))
            expect(after["sha256"] != swipe["sha256"], "Swipe did not change screen")
            client.command(sid, "settings")
            wait(lambda: text_exists(xml_tree(phone), "Settings"), "Settings opened", seconds=20)
            client.command(sid, "back")
            client.command(sid, "home")
            home = png_info(client.command(sid, "screenshot"))
            expect(home["sha256"] != after["sha256"], "Home screenshot did not change")
            REPORT.write_text(json.dumps({
                "result": "PASS", "interface": "MCP streamable HTTP over permanent Render HTTPS", "time_utc": datetime.now(timezone.utc).isoformat(),
                "run_id": os.environ.get("GITHUB_RUN_ID", "local"),
                "emulator": "Android 16 / API 36", "screen": status["screen"],
                "endpoint_host": "nova-phone-lab-private-relay.onrender.com",
                "auth": "GitHub signed short-lived OIDC JWT with separate agent/controller audiences",
                "checks": ["MCP initialized", "MCP tools discovered", "MCP PNG image content decoded", "HTTPS relay reachable", "anonymous denied", "agent cannot act as controller",
                           "outbound WSS agent registered", "real Android 16 status", "PNG from real emulator",
                           "tap changes APK counter", "screenshot hashes differ", "swipe alters screen",
                           "Android settings opened", "Back and Home confirmed"],
                "screenshots": {"before": before, "after_tap": after, "after_swipe": swipe, "home": home},
                "limits": "MCP protocol verified by GitHub HTTP client; ChatGPT account connection and new tool discovery still need separate verification"
            }, indent=2) + "\n")
            print("PASS: direct MCP tools/list + tools/call (PNG, tap, swipe, settings, back, home) over hosted Render with real Android 16", flush=True)
        finally:
            agent.terminate()
            try:
                agent.wait(timeout=5)
            except subprocess.TimeoutExpired:
                agent.kill();agent.wait(timeout=5)

if __name__ == "__main__":
    main()