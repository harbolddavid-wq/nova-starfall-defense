# Nova Pocket Holodeck
Native Android home for portable interactive experiences. Phase 1, 0.1.0.

This isolated branch contains only Pocket Holodeck; main and other projects are untouched.

## Experience
Starwell — The Listening Sky. Carry starlight from the well to marked stars, complete three constellations, uncover a short story, then compose your own sky. Local progress, generated musical soundscape, touch trails and animated celestial objects. All content works offline.

## Function + Feel + Flourish
- Home: launch/resume; immediate touch ripple and native transitions; a continuously drawn, touch-reactive portal.
- Library: real installed experience; single-tap entry; living orbit artwork, offline status.
- Player: complete 3-chapter story/game, drag and tap alternative; snapping, trails and synchronized tones; unfolding constellation drawings, particles and an ending with free sky creation.
- Settings: persisted sound/motion preferences; large touch targets; preview reflects reduced motion.

## Runtime boundary
No Android JavaScript bridge, no app permissions, no network permission, no file/content access, no popups or external navigation, no third-party scripts. Each experience gets a distinct HTTPS virtual origin served from a strict packaged-asset allowlist. CSP blocks external connections, forms, frames and objects. Web storage is origin-scoped. New package delivery/validation is deliberately a future phase; Phase 1 only runs the bundled trusted package. A WebView renderer is defense in depth, not a promise that arbitrary untrusted code is harmless.

## Build and verification
GitHub Actions uses standard free public-repository Ubuntu runners, Gradle 8.13, SDK 36, Java 17, and an API 36 emulator at 720×1600 / 260 dpi / 4 GB RAM. Instrumentation tests check navigation, persistent settings, actual JS interactions and completion, reload/resume, and denied runtime requests. Evidence and APK are uploaded, and release publication requires passing emulator tests. Build locally using Gradle 8.13: `gradle :app:assembleDebug`.

First-install candidate uses the Android debug signer, preserved from the previous release for update continuity in this isolated project. It is a test build, not a store production signing setup. Never commit private signing keys. Future production signing requires a deliberate stable-key migration before distribution grows.

## Next milestone
A signed manifest/package catalog, bounded downloads and integrity checks, atomic versioned installation, per-experience origins, offline cache, rollback and a connected publishing tool. No host reinstallation for compatible HTML/CSS/JS packages.

## Art and sound
Original procedural portal/galaxy art and synthesized Web Audio score/cues created for this project; system fonts only. Existing Library asset catalog inspected; no unknown-license assets redistributed. No third-party artwork or sampled recordings. Native Android framework, Android WebView and build dependencies remain subject to their own licenses.
