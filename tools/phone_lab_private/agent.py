"""Outbound-only Android agent; runs beside ADB on a PRIVATE emulator host.

Connect using WSS in production. No incoming sockets or external ADB port.
"""
import asyncio
import base64
import json
import os
from urllib.parse import urlparse
import websockets

from phone_lab_live_core import PhoneLab


def is_safe_url(url: str) -> bool:
    parsed = urlparse(url)
    if parsed.scheme == "wss":
        return True
    return parsed.scheme == "ws" and parsed.hostname in ("localhost", "127.0.0.1", "::1")


def dispatch(phone: PhoneLab, cmd: dict) -> dict:
    action = cmd.get("action")
    if action == "screenshot":
        return {"image_base64": base64.b64encode(phone.screen_png()).decode("ascii")}
    if action == "status":
        return {"result": phone.status()}
    if action == "tap":
        return {"result": phone.tap(int(cmd["x"]), int(cmd["y"]))}
    if action == "swipe":
        return {"result": phone.swipe(int(cmd["x1"]), int(cmd["y1"]), int(cmd["x2"]), int(cmd["y2"]), int(cmd.get("duration_ms", 450)))}
    if action == "back":
        return {"result": phone.back()}
    if action == "home":
        return {"result": phone.home()}
    if action == "settings":
        return {"result": phone.settings()}
    raise ValueError("Unsupported action")


async def main():
    url = os.environ.get("PHONE_LAB_RELAY_WSS_URL", "")
    token = os.environ.get("PHONE_LAB_AGENT_TOKEN", "")
    if not is_safe_url(url) or not token:
        raise SystemExit("Need a wss:// relay URL (or local loopback ws://) and agent token")
    phone = PhoneLab()
    # Reconnect on transient failures. A new session is created each reconnect.
    while True:
        try:
            async with websockets.connect(url, additional_headers={"Authorization": f"Bearer {token}"},
                                          max_size=12_000_000, ping_interval=15) as ws:
                ready = json.loads(await ws.recv())
                print("Agent ready; ephemeral session:", ready.get("session_id"), flush=True)
                await ws.send(json.dumps({"type": "hello", "device": phone.status()}))
                while True:
                    msg = json.loads(await ws.recv())
                    if msg.get("type") == "ping":
                        await ws.send(json.dumps({"type": "pong"}))
                        continue
                    if msg.get("type") != "command":
                        continue
                    rid = msg.get("request_id")
                    try:
                        payload = await asyncio.to_thread(dispatch, phone, msg)
                        result = {"type": "result", "request_id": rid, "ok": True, **payload}
                    except (ValueError, KeyError, RuntimeError, OSError) as exc:
                        result = {"type": "result", "request_id": rid, "ok": False,
                                  "error": str(exc)[:150]}
                    await ws.send(json.dumps(result, separators=(",", ":")))
        except (ConnectionError, OSError, websockets.exceptions.WebSocketException) as exc:
            print("Relay disconnected; will retry:", type(exc).__name__, flush=True)
            await asyncio.sleep(3)


if __name__ == "__main__":
    asyncio.run(main())