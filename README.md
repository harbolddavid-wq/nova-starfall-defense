# Nova Pocket Holodeck
Native Android home for portable interactive experiences. Phase 2, 0.2.0.

This isolated branch contains only Pocket Holodeck; main and other projects are untouched.

## Experience
Starwell — The Listening Sky. Carry starlight from the well to marked stars, complete three constellations, uncover a short story, then compose your own sky. Local progress, finite musical chimes, touch trails and animated celestial objects. All content works offline.

## Function + Feel + Flourish
- Home: launch/resume; immediate touch ripple and native transitions; a continuously drawn, touch-reactive portal.
- Library: real installed experience; single-tap entry; living orbit artwork, offline status.
- Player: complete 3-chapter story/game, drag and tap alternative; snapping, trails and synchronized tones; unfolding constellation drawings, particles and an ending with free sky creation.
- Settings: persisted sound/motion preferences; large touch targets; preview reflects reduced motion.

## Downloadable worlds
Discover downloads a signed catalog and compatible world packages from GitHub Releases. Lanternwake — The Harbor Beyond is a separate download: steer a small boat across three night-sea passages, rescue nine lanterns, avoid drifting shadow reefs, and find the ending. Installed worlds play offline and retain their own local saves. World options can restore the preceding installed version or remove a download while retaining its save.

## Runtime boundary
The native host has INTERNET permission for catalog and package delivery. WebView content has no unrestricted Android JavaScript bridge, network access, file/content access, popups or external navigation. Each world has a distinct HTTPS virtual origin served from its declared local inventory. CSP blocks external connections, frames, workers, forms and objects. Saves are origin-scoped. Sound uses only the bounded, session-authenticated cue endpoint described below.

The APK certificate verifies domain-separated RSA catalog signatures. Catalogs carry monotonic revisions, immutable versioned package URLs and SHA-256 hashes. Downloads and ZIP expansion have size/file-count limits; extraction rejects traversal, duplicate paths and missing inventory. Staged versions activate through an atomic pointer and retain one previous version. A WebView renderer provides defense in depth; publish only reviewed, owned or licensed experiences.

## Build and verification
GitHub Actions uses standard free public-repository Ubuntu runners, Gradle 8.13, SDK 36, Java 17, and an API 36 emulator at 720×1600 / 260 dpi / 4 GB RAM. Instrumentation tests check navigation, persistent settings, actual JS interactions and completion, reload/resume, and denied runtime requests. Evidence and APK are uploaded, and release publication requires passing emulator tests. Build locally using Gradle 8.13: `gradle :app:assembleDebug`.

First-install candidate uses an isolated per-app test signer generated outside source control at `.signing/candidate.keystore`, retained in the branch-scoped GitHub Actions cache. The next build was verified to restore that key. Publication refuses an update if a prior release exists and the signer cache is unavailable. An encrypted owner recovery backup now protects the original key independently of cache expiry. The private recovery key is held in David’s private recovery kit, never the repository. Recovery uses an encrypted exchange; the cloud checks the recovered certificate fingerprint before accepting it. Never commit plaintext private signing keys. This is a sideloadable test candidate, not a Play Store production release.

## Publishing and next milestone
Compatible worlds publish independently through [PUBLISHING.md](PUBLISHING.md). Content-only publishing does not rebuild the host. Next: connect this proven delivery route to Nova’s publishing plugin, then expand the library.

## Art and sound
Original procedural portal/galaxy art and synthesized finite PCM/Web Audio cues created for this project; system fonts only. Existing Library asset catalog inspected; no unknown-license assets redistributed. No third-party artwork or sampled recordings. Native Android framework, Android WebView and build dependencies remain subject to their own licenses.

## Delivered milestone

Phase 2 build, browser playthroughs and nine Android 16 device checks passed on November 12, 2026. Real catalog download, a separately downloaded world, full airplane-mode playthrough, save retention, data-only update/rollback and prior-APK save migration are verified. See [VERIFIED.md](VERIFIED.md).

[Download APK](https://github.com/harbolddavid-wq/nova-starfall-defense/releases/download/holodeck-v0.2.0/Nova_Pocket_Holodeck_v0.2.0.apk) · [Build and test run](https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/43258163758)

Install over the existing Holodeck app, then open Discover and download Lanternwake. It then opens from Library offline. Starwell is still bundled and retains its previous save. David confirmed the preceding 0.1.2 game works well on his BLU; the new 0.2.0 delivery and Lanternwake still need his physical-phone test.

## 0.1.1 sound repair

David reported complete silence on the physical BLU. Original engine-state tests did not establish audible output. Android now plays original synthesized PCM with a native AudioTrack media/game pipeline, clear entrance chime, brighter harmonics and a more audible ambient mix. Phone volume keys explicitly adjust media. Settings includes a test chime. Browser previews retain Web Audio.

The player can request only a fixed list of musical cues and mute/on through a same-origin, per-session authenticated virtual image endpoint. Requests never leave the device. No JavaScript interface or unrestricted Android APIs are exposed. Cue playback is bounded, respects audio focus, stops on pause/exit, and synchronizes the in-world mute with the native saved setting. Audio tests now require non-silent PCM and advancement of the Android playback head. This is still not an acoustic test of David’s speaker.

## 0.1.2 — quiet background

David confirmed 0.1.1 sound works on the BLU, but heard a steady hum/buzz. Removed the continuous synthesized tone layer from both Android and browser playback. Kept finite entrance, touch and success cues. Audio tracks now release after each tail, and idle playback does not advance audio frames. Same signer, package, experience origin and saved sky. Six API 36 device tests passed, including the new quiet-background assertions.
