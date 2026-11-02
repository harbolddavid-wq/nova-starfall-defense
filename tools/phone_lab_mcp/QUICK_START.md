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

## State retention experiments (November 2)

Synthetic-only Android 16 snapshot restart passed on the same disposable runner: https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/41456800923 . Counter 1 remained 1 after emulator stop and snapshot reload, then advanced to 2. This does not persist beyond runner termination. A cross-runner synthetic full-snapshot attempt created a 4,167,101,011-byte compressed archive and correctly stopped before upload due to the 200 MiB test cap: https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/41457334391 . The cold-boot test under `.github/workflows/nova-phone-lab-cold-state-proof.yml` is experimental and synthetic-only; consult the exact latest run outcome. Never use public artifact/cache for actual private app data, signed APKs, secrets or photos. A private durable backend, checkpoint consent, retention, protected credential handling and recovery after full runner termination are still required for real use. Render Free local filesystem is ephemeral; GitHub runners are temporary.

## Cold-boot state experiment (November 2)

Run https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/41459543950 successfully installed the synthetic fixture, wrote and read a synthetic file under its private app directory and set an Android setting before shutdown. The archive excluding snapshots and cache compressed to 1,170,579,336 bytes and was rejected by the 200 MiB test limit before any upload; the new-runner restore job was skipped. No actual user app or private Android state was collected. No successful cross-runner persistence claim is supported. The workflow at `.github/workflows/nova-phone-lab-cold-state-proof.yml` remains synthetic-only and experimental. Diagnose file sizes or select a suitable explicitly private, durable backend before another attempt; do not raise free-tier limits or risk charges without authorization.

Hosting research: Oracle's Always Free ARM VM is a candidate for a host account but its documentation says nested virtualization is unavailable on virtual ARM instances; x86 AMD free VMs also do not support nested virtualization according to Oracle documentation. Android's unsupported software emulation would need a real performance test, and creating a new account usually requires payment-card identity verification. This is not a verified free always-on emulator. An existing compatible user-controlled computer could potentially host the current agent on persistent local storage if available, without assuming one exists.


## Synthetic app-only save and resume proof (November 2)

Run https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/41461966193 passed on two separate GitHub-hosted runner jobs using fresh Android 16 emulators. The synthetic debuggable fixture now saves its counter in SharedPreferences. The first job tapped to 2, force-stopped the app, exported only its shared_prefs via run-as (2,560-byte uncompressed tar), and transferred it alongside the exact debug APK in a one-day synthetic-only artifact. SHA-256 verified both files on the second runner, which installed the APK, restored preferences and observed counter 2, then tapped and observed 3. Run 41461401563 failed before backup when automated taps were too close together; pacing fixed the subsequent test. This demonstrates only a single synthetic app's saved preference, not Android OS state, unsaved in-memory state, app databases/files, or arbitrary applications. run-as requires a debuggable app; release APKs and keystore-bound secrets can require other approaches.

This is an isolated test, not integrated into Phone Lab quick start or the live relay; it does not make the emulator continuously available. Its artifact is unencrypted and suitable solely for deliberately synthetic public test data; never use it for real private apps or data. Before actual use implement an explicitly private encrypted durable store, secure key handling, selective app backup including databases/files where supported, app version and signer compatibility, atomic/verified restore, and integration with live session lifecycle. App-only saves do not reproduce current screen or process RAM. The user has no PC, so do not depend on one for hosting.
