# Nova Phone Lab quick start

The Android 16 emulator runs on a temporary GitHub Actions runner. Render hosts the authenticated relay, not the emulator. This workflow does not provide a permanently running device.

To request a session, first call the connected Nova Phone Lab list_sessions. Reuse a connected session if one exists. If no session is connected, update only tools/phone_lab_mcp/trigger.txt in the experiment/nova-phone-lab-live-2026-11-01 branch using the GitHub connector, changing the request number and including these exact lines:

    mode=quick_start
    purpose=direct_chatgpt_mcp_live_android16_interaction
    session_limit=1800_seconds
    synthetic_only=true

That file update starts the existing direct-MCP workflow. Wait for a live session from list_sessions and use its returned ID. The workflow still starts a GitHub runner; this is a one-request convenience, not a replacement compute host. Concurrent requests cancel the previous workflow, so do not trigger when an active session should be preserved. Only the public synthetic fixture is supported.

Quick start skips the full smoke test, which previously used a separate agent before starting the interactive agent. The usual authenticated agent registration still runs and direct device status can be checked through MCP. For full proof remove the mode=quick_start line and update the request number, then inspect the workflow result; don't confuse a quick-start session with a completed full test.

The relay authentication, scoped GitHub OIDC, bounded 30-minute runner lifetime and existing emulator parameters are unchanged. No private APK, photograph, session token or credential goes in the repository, workflow logs or artifacts.

## Verified quick-start run

Run https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/41385476849 reached a connected Android 16 emulator. The connected ChatGPT MCP tools returned device status and an actual screen image, then a tap on the synthetic fixture changed the visible counter from 0 to 1. The runner was still active when verified; completion of the full job is not claimed. An earlier test run failed because the emulator action executes script lines separately; the conditional is now a single shell line.

## Extended-window supervisor

The experimental 30-minute hold runs `live_supervisor.py`; every four minutes it renews the separate GitHub OIDC agent/controller identities and reconnects only the agent. The Android emulator stays running, while the session ID changes and must be rediscovered with `list_sessions` after each rotation. A brief reconnect interruption is expected. The job stops after 30 minutes and any temporary emulator data is lost; there is no cross-restart persistence or always-on host. Use only synthetic fixtures in this public CI setup. `python3 -m unittest discover -s tools/phone_lab_mcp -p 'test_live_supervisor.py'` checks renewal, identity matching, agent failure and duration bounds. A real test verified one four-minute credential rotation and preserved Android app state, but the entire 30-minute duration and state after runner termination remain unverified.

## Extended-window live verification

Run https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/41386259396: ChatGPT MCP connected to a real Android 16 test emulator, tapped the synthetic test fixture counter from 0 to 1, waited through one scheduled agent rotation, re-discovered the new session, observed the counter still at 1, and tapped again to reach 2. The runner was active at this verification; no full 30-minute completion or permanent host claim is made. During rotation the relay briefly listed the previous disconnected session alongside the new one. Re-discover sessions and check device_status or see_screen before issuing commands; a stale session may time out. Do not start a new runner while a healthy session exists.
