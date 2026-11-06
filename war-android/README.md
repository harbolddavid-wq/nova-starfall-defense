# WAR — The Visual Challenge (v0.1.0 Android playtest)

Independent, offline Android War game inspired by original generated red-and-blue heraldic battlefield paintings. **Not an expansion of Nova Cards**.

## Runtime

- Classic two-player War: 52 unique cards, 26 per side, aces high, winning cards returned to bottom of winner's queue.
- Automatic 3-down/1-up WAR escalation, using fewer buried cards when one side has insufficient cards, always reserving its final reveal.
- No available reveal card means forfeit. When both sides are completely exhausted at the same time, a last-resort final suit comparison (♠ > ♥ > ♦ > ♣) resolves an otherwise unfinishable deadlock. This is disclosed in the in-app rules.
- Card conservation enforced after each engine operation. Current match survives closing the app when WebView storage is available.
- Painted card fronts/backs, two actually illustrated battlefield theaters, animated simultaneous deals and flips, live captured-card counter, cinematic War blast, particles, original synthesized impact/ceremony sounds, auto battle, landscape/portrait layout and safe system insets, custom launcher artwork.
- No account, network permission, ads, in-app purchases, real-money betting, or third-party trackers.

## Build

Java 17, Android SDK 35, Android Gradle Plugin 8.7.3, Gradle 8.9.

```
gradle -p android :app:assembleDebug --no-daemon
```

GitHub Actions build is isolated to the `war-visual-challenge-build` branch of `harbolddavid-wq/nova-starfall-defense`, under `war-android/` and its own workflow. It does not modify the official Nova Family Store.

## Source files

- `android/app/src/main/assets/index.html`: UI
- `.../styles.css`: responsive art layout and animated effects
- `.../engine.js`: rules engine with no Android or DOM dependencies
- `.../game.js`: animation and interaction controller
- `.../assets/`: cropped and optimized real art assets
- `android/app/src/main/java/site/novasky/war/MainActivity.java`: native offline WebView and Android system-bar insets
- `tests/engine.test.js`: correctness and conservation
- `tests/browser_smoke.py`: portrait, landscape, WAR and touch smoke tests
- `make_assets.py`, `assets/MANIFEST.json`: artwork extraction, sources, sizes and SHA-256 hashes

## Verification and release status

Engine tests and Chromium screenshots completed locally. GitHub Android compilation and real-phone testing are independent milestones. Do not describe this as a final Family Store release unless installation and gameplay have been checked on David's phone and he approves visual quality. Any debug APK is a playtest candidate, using ephemeral GitHub runner signing.

Original art from the direct user-authorized WAR challenge in this conversation; transforms are documented in assets/MANIFEST.json. No license-uncertain library material was embedded. Source and art preserved together in this bundle.