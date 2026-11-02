"""Keep one synthetic Android emulator available through bounded agent renewals.

No credentials, session IDs, screen content, or private app data are logged.
The Android process is not restarted when the outbound authenticated agent rotates.
"""
from __future__ import annotations

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

HOLD_SECONDS = 1800
ROTATE_SECONDS = 240
REGISTER_SECONDS = 45


def stop_agent(agent):
    if agent.poll() is None:
        agent.terminate()
        try:
            agent.wait(timeout=5)
        except subprocess.TimeoutExpired:
            agent.kill()
            agent.wait(timeout=5)


def run_supervisor(*, get_token, start_agent, get_sessions, now, sleep,
                   hold_seconds=HOLD_SECONDS, rotate_seconds=ROTATE_SECONDS,
                   register_seconds=REGISTER_SECONDS):
    """Rotate short-lived agent tokens while retaining the running Android VM."""
    if not (1 <= rotate_seconds <= hold_seconds <= 3600 and 1 <= register_seconds <= 60):
        raise ValueError("Invalid hold, renewal or registration interval")
    end = now() + hold_seconds
    while now() < end:
        # A fresh controller token is used only for registration checks.
        controller_token = get_token("nova-phone-lab-controller")
        old_ids = {s.get("session_id") for s in get_sessions(controller_token)}
        agent_token = get_token("nova-phone-lab-agent")
        agent = start_agent(agent_token)
        try:
            registration_deadline = min(end, now() + register_seconds)
            registered = False
            while now() < registration_deadline:
                if agent.poll() is not None:
                    raise RuntimeError("Android agent exited before registering")
                registered = any(s.get("session_id") not in old_ids and
                                 str(s.get("device", {}).get("api_level")) == "36"
                                 for s in get_sessions(controller_token))
                if registered:
                    break
                sleep(min(2, max(0, registration_deadline-now())))
            if not registered:
                raise RuntimeError("Android agent did not register")
            print("LIVE: Android 16 session connected; short-lived access will refresh.", flush=True)
            rotate_at = min(end, now() + rotate_seconds)
            while now() < rotate_at:
                if agent.poll() is not None:
                    raise RuntimeError("Android agent exited unexpectedly")
                sleep(min(3, max(0, rotate_at-now())))
        finally:
            stop_agent(agent)
    print("LIVE SESSION ENDED: bounded runner shutting down.", flush=True)


def main():
    subprocess.run(["adb", "shell", "am", "start", "-n", FIXTURE],
                   check=True, stdout=subprocess.DEVNULL)
    with open(os.devnull, "w") as sink:
        def spawn(token):
            env = dict(os.environ, PHONE_LAB_AGENT_TOKEN=token,
                       PHONE_LAB_RELAY_WSS_URL=BASE.replace("https://", "wss://", 1)+"/v1/agent")
            return subprocess.Popen([sys.executable, str(ROOT/"phone_lab_private"/"agent.py")],
                                    env=env, stdout=sink, stderr=sink)
        def sessions(token):
            return PrivatePhoneLab(base_url=BASE, token=token).sessions().get("sessions", [])
        run_supervisor(get_token=oidc, start_agent=spawn, get_sessions=sessions,
                       now=time.monotonic, sleep=time.sleep)

if __name__ == "__main__":
    main()
