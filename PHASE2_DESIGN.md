# Phase 2 — experience delivery

Function + Feel + Flourish:

- Discover: verified live catalog and honest offline cache; one-tap downloads with native progress and readable retry messages; moving sea/portal previews and confirmation after installation commits.
- Library: installed versions open offline, update/rollback never removes progress; immediate native launch and useful world options; distinct living previews faithful to the actual worlds.
- Player: one origin and storage space per experience, strict local-file inventory, no unrestricted Android bridge; drag steering plus buttons; animated water, lantern flames, boat rocking/wakes, responsive finite musical cues and a complete three-passage ending. No continuous hum.
- Delivery: signed catalog trusted by the installed APK certificate, SHA-256 package inventory, bounded HTTPS downloads and ZIP expansion, staged atomic activation, one previous version.

First proof: install Phase 2 host over 0.1.2 preserving a real saved star. Publish Lanternwake after that host is installed, download it through Discover, play offline, update world data and roll back without changing the APK or saved voyage.

GitHub Releases hosts signed catalog and immutable world ZIPs at holodeck-worlds. Content-only commits trigger publish-holodeck-worlds.yml, which never builds an APK. Host commits use [host] and the host workflow publishes packages inside its device test after installation.
