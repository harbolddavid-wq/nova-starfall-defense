#!/usr/bin/env python3
"""Local MCP tools for Nova Phone Lab Live.

Prototype only: this starts a LOCAL MCP server through stdio. It is not an
internet-hosted ChatGPT integration, and it is not continuous video. A private
authenticated relay/host must exist before ChatGPT can use these tools.
"""
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mcp.server import MCPServer
from mcp.server.mcpserver import Image
from phone_lab_live_core import PhoneLab

mcp=MCPServer("Nova Phone Lab Live")
phone=PhoneLab()

@mcp.tool()
def see_screen() -> Image:
    """Capture a fresh PNG of the connected Android 16 test emulator for visual inspection."""
    return Image(data=phone.screen_png(),format="png")

@mcp.tool()
def device_status() -> dict:
    """Read real Android emulator SDK/display status. Does not claim a physical BLU match."""
    return phone.status()

@mcp.tool()
def tap_screen(x:int,y:int) -> dict:
    """Tap a screen coordinate on the disposable test emulator. Follow with see_screen."""
    return phone.tap(x,y)

@mcp.tool()
def swipe_screen(x1:int,y1:int,x2:int,y2:int,duration_ms:int=450) -> dict:
    """Swipe on the test emulator. Follow with see_screen to confirm UI response."""
    return phone.swipe(x1,y1,x2,y2,duration_ms)

@mcp.tool()
def press_back() -> dict:
    """Press Android's Back key in the active test emulator."""
    return phone.back()

@mcp.tool()
def press_home() -> dict:
    """Press Android's Home key in the active test emulator."""
    return phone.home()

@mcp.tool()
def open_android_settings() -> dict:
    """Open system Settings in the disposable test emulator."""
    return phone.settings()

@mcp.tool()
def inspect_screen_ui() -> str:
    """Read Android's exposed UI hierarchy; not invisible application internals."""
    return phone.ui_hierarchy()

if __name__=="__main__":
    # Deliberately local only. Do not expose an unauthenticated public HTTP
    # endpoint. Deploy through a private authenticated host/relay later.
    mcp.run(transport="stdio")
