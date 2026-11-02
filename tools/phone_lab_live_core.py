#!/usr/bin/env python3
"""ADB-backed, allowlisted local controller for a disposable Android test emulator.

No arbitrary shell execution. No inbound network listener. This deliberately
does NOT publish an unauthenticated emulator to the internet.
"""
import re
import subprocess
from dataclasses import dataclass

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MAX_FRAME = 8_000_000

@dataclass
class PhoneLab:
    adb_exe: str = "adb"

    def adb(self, *args: str, timeout: int = 15) -> bytes:
        completed = subprocess.run(
            [self.adb_exe, *map(str,args)], capture_output=True,
            timeout=timeout, check=True
        )
        return completed.stdout

    def dimensions(self) -> tuple[int,int]:
        output = self.adb("shell","wm","size").decode("utf-8","replace")
        m = re.search(r"Override size:\s*(\d+)x(\d+)",output)
        if not m:
            m = re.search(r"Physical size:\s*(\d+)x(\d+)",output)
        if not m:
            raise RuntimeError("Could not determine emulator screen dimensions")
        width,height = map(int,m.groups())
        if width<=0 or height<=0:
            raise RuntimeError("Invalid Android screen size")
        return width,height

    def screen_png(self) -> bytes:
        data=self.adb("exec-out","screencap","-p",timeout=20)
        if not data.startswith(PNG_SIGNATURE) or len(data)>MAX_FRAME:
            raise RuntimeError("No valid screenshot received")
        return data

    def _xy(self,x:int,y:int) -> tuple[int,int]:
        width,height = self.dimensions()
        if not (0<=x<width and 0<=y<height):
            raise ValueError(f"Coordinate outside current Android display ({width}x{height})")
        return x,y

    def tap(self,x:int,y:int)->dict:
        self._xy(x,y)
        self.adb("shell","input","tap",x,y)
        return {"action":"tap","x":x,"y":y,"status":"command_sent","note":"Get a fresh screenshot to verify UI response"}

    def swipe(self,x1:int,y1:int,x2:int,y2:int,duration_ms:int=450)->dict:
        self._xy(x1,y1)
        self._xy(x2,y2)
        if not 100<=duration_ms<=3000:
            raise ValueError("Swipe duration must be 100-3000 milliseconds")
        self.adb("shell","input","swipe",x1,y1,x2,y2,duration_ms)
        return {"action":"swipe","status":"command_sent","note":"Get a fresh screenshot to verify UI response"}

    def back(self)->dict:
        self.adb("shell","input","keyevent","KEYCODE_BACK")
        return {"action":"back","status":"command_sent"}

    def home(self)->dict:
        self.adb("shell","input","keyevent","KEYCODE_HOME")
        return {"action":"home","status":"command_sent"}

    def settings(self)->dict:
        self.adb("shell","am","start","-a","android.settings.SETTINGS")
        return {"action":"settings","status":"command_sent"}

    def status(self)->dict:
        return {
            "api_level":self.adb("shell","getprop","ro.build.version.sdk").decode().strip(),
            "screen":f"{self.dimensions()[0]}x{self.dimensions()[1]}",
            "connected":True
        }

    def ui_hierarchy(self)->str:
        self.adb("shell","uiautomator","dump","/sdcard/nova-phone-lab.xml",timeout=25)
        content=self.adb("exec-out","cat","/sdcard/nova-phone-lab.xml",timeout=15).decode("utf-8","replace")
        if not content.lstrip().startswith("<?xml"):
            raise RuntimeError("No valid UI hierarchy found")
        return content[:150_000]
