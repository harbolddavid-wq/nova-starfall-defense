# Nova Phone Lab quick start

The Android 16 emulator runs on a temporary GitHub Actions runner. Render hosts the authenticated relay, not the emulator. This workflow does not provide a permanently running device.

To request a session, first call the connected Nova Phone Lab list_sessions. Reuse a connected session if one exists. If no session is connected, update only tools/phone_lab_mcp/trigger.txt in the experiment/nova-phone-lab-live-2026-11-01 branch using the GitHub connector, changing the request number and including these exact lines:

    mode=quick_start
    purpose=direct_chatgpt_mcp_live_android16_interaction
    session_limit=420_seconds
    synthetic_only=true

That file update starts the existing direct-MCP workflow. Wait for a live session from list_sessions and use its returned ID. The workflow still starts a GitHub runner; this is a one-request convenience, not a replacement compute host. Concurrent requests cancel the previous workflow, so do not trigger when an active session should be preserved. Only the public synthetic fixture is supported.

Quick start skips the full smoke test, which previously used a separate agent before starting the interactive agent. The usual authenticated agent registration still runs and direct device status can be checked through MCP. For full proof remove the mode=quick_start line and update the request number, then inspect the workflow result; don't confuse a quick-start session with a completed full test.

The relay authentication, scoped GitHub OIDC, short session lifetime and existing emulator parameters are unchanged. No private APK, photograph, session token or credential goes in the repository, workflow logs or artifacts.
