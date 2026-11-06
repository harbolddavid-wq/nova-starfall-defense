# WAR: The Visual Challenge — READY FOR DEPLOYMENT

**Status:** READY FOR NOVA FAMILY STORE PUBLICATION — David approved the game after testing it on his phone, November 6, 2026. **Not yet published.**

This is an independent illustrated Android game, **not** an expansion of Nova Card Collection.

## Approved build

- Title: WAR: The Visual Challenge
- Version: **0.1.0**, Android `versionCode` **1**
- Android package: `site.novasky.warvisual`
- Installable APK: `WAR_The_Visual_Challenge_v0.1.0_Android_Playtest.apk`
- SHA-256: `0cb5e08cc0e97f6a5e18daf5325c5f5bb468a11438b52313e38d5424e0f3d1b6`
- Complete backup (source, original/derived art, APK, tests): `WAR_Visual_Challenge_v0.1.0_Complete_Playtest_Archive.zip`
- ChatGPT Library: `/WAR — The Visual Challenge/`
- GitHub build repo: `harbolddavid-wq/nova-starfall-defense`
- Source branch: `war-visual-challenge-build`
- Tested build source commit: `29b565b87f6ea8e4b856e7099c9a399c26accb7c`
- Successful GitHub Actions workflow run: `42295773394`

## Approval and testing evidence

- David's explicit feedback after playing: **“Best visual game you have made smooth animations fun to tap yes I'm including work games so ‘Nova floops the pig back’ I'm impressed.”**
- Browser visual/gameplay tests: portrait and landscape, simultaneous flips, real painted art assets, escalated WAR sequence, themed screens, victory, reset, auto play.
- Engine tests: 26/26 initial dealing, ties, WAR sequence, insufficient-card behavior, conservation of all 52 cards, 3,000-step simulation and save/restore.
- GitHub build: Android APK compilation, integrity check, and artifact upload all succeeded. APK archive was inspected and SHA-256 checked.

## Publishing handoff — destination and scope

- **Official live storefront:** https://nova-family-store.hatchable.site/
- **Hatchable project ID:** `proj_ba1OSj9QpeR5`
- The live site uses a static `public/index.html` catalog and `public/store.js` for filtering/browsing. APK downloads are hosted by GitHub Releases. **Do not use the retired experimental Family Store.**
- Next room: inspect the official live site's existing app-card convention, upload the verified APK to a GitHub Release using the established family publishing route, add exactly one WAR card with a description and real download URL, deploy the Hatchable storefront, and confirm that the public link downloads the exact approved APK.
- Recommended catalog description: “Classic offline two-player War transformed into a cinematic red-vs-blue battlefield: beautifully illustrated cards, simultaneous flips, dramatic tie clashes, effects, sound, and two painted themes. One-tap play, optional auto battle, portrait/landscape support. No ads or internet required.”
- Suggested section: Games. Do **not** claim it is in the store until the final release link and storefront listing are verified.

## Constraints and cautions

- Preserve the exact approved APK bytes. Do not rebuild, replace artwork, alter gameplay, or re-sign while merely publishing without a documented reason and David's approval.
- Current APK uses an **ephemeral debug signing key**. The signing key is not retained. This is acceptable for a private family sideload, but installing later updated versions may require reinstalling rather than updating in place.
- No paid services or spending. No unnecessary store redesigns. Preserve source and visual art provenance in the project archive.

**Next action:** Publish this approved APK through the **existing** Nova Family Store, then verify download/installation. 