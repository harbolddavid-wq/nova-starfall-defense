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


## Encrypted backup preparation (November 2)

Added checkpoint_crypto.py: AES-256-GCM with a fresh random nonce and app package plus exact APK SHA-256 authenticated as associated data. It rejects tampering, wrong keys or app identity and limits checkpoint size to 32 MiB. Added checkpoint_lifecycle.py, a callback-based fail-closed coordinator: verified restoration must precede session startup and a successful capture precedes a verified write on normal completion. Eight local unit tests passed with Python cryptography 50.0.2. These modules are not yet connected to the live supervisor, an Android data exporter, a key manager or a real storage backend. They do not yet encrypt actual Phone Lab saves or provide automatic live persistence. Uncatchable cancellation cannot be handled by normal shutdown hooks; periodic checkpoints and recovery need separate design and testing.

Storage investigation: connected GitHub installation exposed only public repository harbolddavid-wq/nova-starfall-defense and offered no repository-creation or Actions-secret tool. The ChatGPT Google Drive connection does not by itself grant the GitHub runner an OAuth credential. No verified durable private runner-writable store or safely provisioned key was available. Do not silently fall back to plaintext, a public artifact, public Git data, or a Render ephemeral filesystem. A private repository plus narrowly scoped runner credentials and a separately protected encryption key is a candidate, to be set up and verified before integrating. Do not place credentials or private data in public commits, artifacts or logs.


## Private repository connected (November 2)

The dedicated repository harbolddavid-wq/nova-phone-lab-backups- is private and available to the ChatGPT GitHub connector. Initialized its README and verified readback. The public-workflow runner still has no credential granting access to that separate repository, nor is a checkpoint encryption key provisioned. Do not confuse connector access with runner access.

Added private_store.py (private-visibility checks, small bounded blobs, optimistic GitHub contents API updates and readback) and checkpoint_service.py (authenticated encryption plus readback verification). Four local tests pass against an in-memory fake GitHub API; these do not prove actual runner access or durable end-to-end save/restore. These modules are not yet wired into live_supervisor.py or deployed. Real private app data must not be used until scoped credential/key provisioning, actual private-repository and Android end-to-end testing, and lifecycle integration succeed. Abrupt termination can still lose unsaved progress. Private repository is a storage target, not free always-on compute.


## Prepared private synthetic end-to-end proof (November 2)

Workflow .github/workflows/nova-phone-lab-private-save-proof.yml and synthetic_private_checkpoint.py are prepared but have NOT been run against the private repository or a live Android emulator. The workflow is opt-in via tools/phone_lab_mcp/private_checkpoint_trigger.txt (not yet created); it deliberately uploads only the public synthetic APK artifact. A synthetic checkpoint is encrypted and read-back verified in the private repository, then retrieved automatically on a separate fresh runner and checked via the counter UI. YAML parsing and two mocked end-to-end script tests passed locally. This proof does not integrate live session persistence.

The public GitHub Actions project requires two repository Actions secrets: PHONE_LAB_BACKUP_TOKEN (fine-grained token limited to harbolddavid-wq/nova-phone-lab-backups- and repository Contents read/write) and PHONE_LAB_CHECKPOINT_KEY (32 random bytes base64). A randomly generated key is stored in David's private Library as NOVA_PHONE_LAB_CHECKPOINT_KEY_PRIVATE.txt for setup and recovery; do not expose or print it, and never regenerate the key after it is used to encrypt saves. The ChatGPT connector does not expose GitHub secret provisioning; David must configure the two secrets on GitHub directly. Never paste secrets into chat, commit them, log them, or put them in artifacts. After secret setup, trigger the proof by creating/updating the named trigger file; verify both jobs before claiming success. Continue to preserve the existing live supervisor unchanged until the end-to-end test passes. Current test uniquely names the synthetic checkpoint per run; private synthetic files are retained until deliberately cleaned up.
