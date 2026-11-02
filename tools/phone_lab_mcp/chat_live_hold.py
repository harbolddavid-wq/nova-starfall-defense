#!/usr/bin/env python3
"""Keep a temporary Android 16 test agent connected for direct ChatGPT MCP commands.

Runs ONLY within the existing trusted GitHub Actions Android emulator workflow.
No token, screenshot, session ID, or private app content is printed to CI logs.
Auto-exits after at most 7 minutes; the relay session also expires independently.
"""
import os
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "phone_lab_private"))

from mcp_hosted_smoke import oidc, BASE, FIXTURE
from controller import PrivatePhoneLab

def main():
    subprocess.run(["adb","shell","am","start","-n",FIXTURE],check=True,stdout=subprocess.DEVNULL)
    agent_token = oidc("nova-phone-lab-agent")
    controller_token = oidc("nova-phone-lab-controller")
    env = dict(os.environ,
               PHONE_LAB_AGENT_TOKEN=agent_token,
               PHONE_LAB_RELAY_WSS_URL=BASE.replace("https://","wss://",1)+"/v1/agent")
    client = PrivatePhoneLab(base_url=BASE, token=controller_token)
    with open(os.devnull, "w") as sink:
        agent=subprocess.Popen([sys.executable,str(ROOT/"phone_lab_private"/"agent.py")],
                              env=env,stdout=sink,stderr=sink)
        try:
            limit=time.monotonic()+45
            active=False
            while time.monotonic()<limit:
                if agent.poll() is not None: raise RuntimeError("Agent exited before connected")
                sessions=client.sessions().get("sessions",[])
                active=any(s.get("device",{}).get("api_level")=="36" for s in sessions)
                if active: break
                time.sleep(2)
            if not active: raise RuntimeError("Android agent did not register with relay")
            print("LIVE: Android 16 connected to private Render relay for direct ChatGPT MCP control (7-minute limit).",flush=True)
            deadline=time.monotonic()+420
            while time.monotonic()<deadline and agent.poll() is None:
                time.sleep(3)
            print("LIVE SESSION ENDED: temporary runner shutting down.",flush=True)
        finally:
            agent.terminate()
            try: agent.wait(timeout=5)
            except subprocess.TimeoutExpired:
                agent.kill()
                agent.wait(timeout=5)

if __name__=="__main__":
    main()
