#!/usr/bin/env python3
"""Exercise MCP tool functions against a real disposable Android emulator."""
import hashlib
import json
import sys
import time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from phone_lab_live_mcp import (device_status, see_screen, open_android_settings,
                                tap_screen, swipe_screen, press_back, press_home)
out=Path("phone-lab-live-evidence")
out.mkdir(exist_ok=True)
status=device_status()
assert status["api_level"]=="36",status
assert status["connected"],status
first=see_screen()
png=getattr(first,"data",None)
assert isinstance(png,bytes) and png.startswith(b"\x89PNG\r\n\x1a\n"),"MCP screenshot did not return PNG"
(out/"mcp-00-home.png").write_bytes(png)
open_android_settings()
time.sleep(3)
after=see_screen().data
(out/"mcp-01-settings.png").write_bytes(after)
assert hashlib.sha256(png).digest()!=hashlib.sha256(after).digest(),"Settings did not visually change screen"
tap_screen(330,1205)
time.sleep(2)
(out/"mcp-02-tapped.png").write_bytes(see_screen().data)
swipe_screen(520,1290,520,450,650)
time.sleep(2)
(out/"mcp-03-swiped.png").write_bytes(see_screen().data)
press_back()
press_home()
time.sleep(1)
(out/"mcp-04-home.png").write_bytes(see_screen().data)
(out/"mcp_tool_test.json").write_text(json.dumps({
 "result":"PASS","note":"Local MCP tool handlers exercised real ADB screenshots and input; remote plugin connection NOT yet configured",
 "device":status,"screenshots":5,"actions":["settings","tap","swipe","back","home"]},indent=2))
print("PASS: local MCP tool handlers got actual PNG, Settings, tap/swipe/back/home",flush=True)
