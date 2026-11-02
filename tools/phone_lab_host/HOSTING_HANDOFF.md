# Nova Phone Lab — private relay hosting handoff (November 2, 2026)

## What changed

An isolated **host-ready** Docker/Render deploy template is provided alongside the existing real Android 16 external-network test. No host account was accessed, no services purchased, and no persistent emulator was provisioned. Until an actual deployment is confirmed, there is **no persistent HTTPS hostname and no remote ChatGPT MCP tool**.

## A host without new paid services

An optional Render **Free** web-service Blueprint lives in `tools/phone_lab_host/render.phone-lab.yaml`. Use the Blueprint path while creating the service from the experiment branch, review the pricing before confirming, and enter two long, independent (minimum 32-character) random bearer credentials as environment variables. They must not be committed into GitHub. The service starts from `Dockerfile` and accepts HTTP only behind Render's own TLS ingress; Render supplies the HTTPS hostname. WebSockets use the corresponding WSS hostname.

**Costs and continuity:** Render's free web services can sleep after 15 minutes without traffic, restart unexpectedly, are subject to 750 free instance-hours per workspace per month, and have bandwidth/build limits; free hosting is not a guarantee of 24/7 operation, unlimited usage, or cost. Consult Render's current limits before activation. These constraints are exactly why we cannot claim a permanent always-on emulator just because a web relay deploys. The Android host remains separate.

## Connecting a running emulator securely

From a **private** emulator host with Android SDK/ADB and our `agent.py`, point:

- `PHONE_LAB_RELAY_WSS_URL=wss://<RELAY_HOST>/v1/agent`
- `PHONE_LAB_AGENT_TOKEN=<agent credential from host>`

For a controller, use:

- `PHONE_LAB_RELAY_HTTPS_URL=https://<RELAY_HOST>`
- `PHONE_LAB_CONTROL_TOKEN=<different controller credential>`

Only outbound WSS leaves the emulator host; do not open an ADB listener to the internet. An Android session is created at connection, renewed after reconnect, and bounded by the relay's TTL. The controller can discover the current session through `/v1/sessions` with its credential and request screenshots/taps/swipes/Settings/Back/Home. No arbitrary shell or APK upload exists in this API; no private images go to public GitHub issues.

## Why an actual persistent *emulator* still needs a host

GitHub-hosted Android 16 CI jobs are disposable and end; they cannot be kept alive forever. Render Free hosts the *small Python relay*, not an Android emulator with accelerated KVM. For a truly persistent Android session, run the agent and Android emulator on a user-controlled always-on Linux computer/VM with KVM and adequate RAM, or a separately selected hosted emulator service. No such machine is linked in this conversation. Costs/quotas must be approved before renting an always-on Android host.

## Connection to ChatGPT

The current `private_mcp.py` is a **local stdio MCP adapter**, not a remotely registered ChatGPT plugin. Connecting directly to ChatGPT still requires a reachable MCP endpoint with the ChatGPT-supported transport, authentication setup, and successful end-to-end tool invocation. Do not misrepresent a public `/v1/command` REST URL as a ChatGPT MCP registration. The next milestone after the stable relay is remote MCP deployment and tool registration, with privacy and access controls validated.

## Security and operation

- Strong distinct bearer secrets are required; the production entrypoint rejects short/matching credentials.
- Keep only one Python Uvicorn worker while sessions are in memory; scaling to multiple replicas needs a shared session broker.
- No screenshot/data persisted by the relay; screenshots are forwarded as `image/png` with `Cache-Control: no-store`.
- Health route reveals only `status: ok`. All session and command APIs require controller credentials.
- Protect source repository permissions and credentials. Rotate both secrets if exposed.
- Quick Tunnel proof is disposable and anonymous; not a persistent address, production endpoint, or secure substitute for authenticated application access.

## Exact next check

1. Verify the GitHub **Remote TLS Real Android Proof** run succeeded using authenticated HTTPS/WSS through a temporary tunnel, not just localhost.
2. Deploy relay on a host the owner explicitly chooses, verify `/health`, unauthorized 401, separate agent/controller access, TLS and WebSocket handshake.
3. Attach Android agent to that host, test `screenshot` and real counter change; add measured latency and downtime behavior.
4. Implement and register a separate remote MCP interface for ChatGPT, only after the relay host exists.