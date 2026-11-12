# Pocket Holodeck 0.1.0 — verified delivery

November 12, 2026. Tested application source commit `b942263392ae66118d3076c291aabd770a45036a`.

- GitHub Actions run 43238180620: successful build, browser playthrough, five device tests and release publication.
- Actual API 36 emulator installation and launch; 720×1600 at 260 dpi, approximately 4 GB RAM. Generic x86_64 software device, not physical BLU/MediaTek hardware.
- Full 15-star playthrough using native-dispatched touch events; all chapters, ending, free sky, exit and re-entry. Separate tap alternative and landscape checks passed.
- Partial progress and ending/free-sky progress persist after closing/reopening the player.
- Native home/library/settings navigation, saved sound/motion settings and real Activity pause/resume passed. AudioContext running/suspended state and mute tested; speakers/headphones were not acoustically evaluated.
- Resource allowlist and file/content/network restrictions checked. No Android JS interface or app permissions.
- Screens inspected: home, library, game, ending, settings, landscape. Paired game captures show 254,447 changing canvas pixels. Complete recording and device logs are included in release evidence.
- Final device logs: zero FATAL EXCEPTION entries, zero app ANRs, zero attached-WebView destruction warnings.
- Software-emulator startup frame stats include jank (74/109 frames counted late in a short home relaunch sample); they do not establish physical-phone smoothness. BLU touch latency, sustained frame pacing, hardware audio and power/temperature remain physical-phone checks. Do not call it fully polished based on these results.
- App signer restored across builds, APK Signature Scheme v2 verification passed. No signing key in source or public release assets. Actions cache is not permanent key backup; preserve identity before future host rebuilds.
- Main branch remained `2e6f0e3fee0e5a36f54429eada53e3d5fa01655d`; no existing app/store source was changed.

## Delivered bytes

Public APK downloaded independently after publication; ZIP CRC validated; SHA-256 matched the tested build artifact.

`Nova_Pocket_Holodeck_v0.1.0.apk` — 30,779 bytes.

SHA-256: `fce4c7a721dace42511f9fab6f5f8322f141935c678c9a21b7135631f2c3008b`

## Continuation

Next milestone: a versioned experience registry, signed package/catalog delivery, bounded atomic downloads, per-experience origins, offline installation/cache and rollback, followed by a connected publishing plugin. Phase 1 has one bundled world and no live backend. Future compatible experience updates should not require host reinstalls.

Keep this isolated project branch separate from main; do not merge its standalone tree over the unrelated root projects.

## 0.1.1 — physical-phone silence report and repair

David reported no audible game sound on the BLU. The original running-AudioContext check did not prove audible output; the ambient mix was also far too quiet for phone speakers. Android now uses original PCM through native AudioTrack with an entrance chime, brighter harmonics, an audible ambient mix, audio focus and media-volume key control. The game mute and native setting now share their saved state. Settings includes a test chime. Saves retain the same package, origin and storage key.

Successful run: https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/43244839090

Tested commit: `87d008cf80752d50877d2f0d08e82b2966a95388`. Six API 36 tests passed, including real native playback-head advancement and non-silent PCM, mute/off-player-reopen/on, Activity pause/resume, all prior game/navigation/save/rotation tests, and same-signer installation over 0.1.0. Audio transitions are asynchronous; tests await completed transitions with bounded timeouts instead of assuming 150–350 ms under emulator load.

Native output report: 142,942 consumed frames; tone PCM RMS 0.09921568; ambient PCM RMS 0.04949583; no playback errors. Final logs contain zero fatal exceptions and zero app ANRs. This proves playback through Android’s virtual audio system, not acoustic BLU speaker output. David’s physical confirmation remains required.

Public release APK independently downloaded and CRC/checksum verified: `Nova_Pocket_Holodeck_v0.1.1.apk`, 38,795 bytes; SHA-256 `05fe0bdc044a0595c4ca3b87a076e0556ed29c20463e0e9709550a3bf39cf72e`. APK v2 signature verification passed.

No unrestricted JavaScript-to-Android interface: only a fixed cue/mute list through a same-origin virtual image endpoint, per-player session token, monotonically ordered messages, fixed waveforms, bounded voice count/cue rate, and foreground checks. External requests and file/content APIs remain blocked.

## 0.1.2 — remove background hum

Physical feedback: David confirmed 0.1.1 sound works on the real BLU, with an unwanted steady hum/buzz. Removed the continuous oscillator bed entirely, while preserving the native cue path that worked on his phone. Browser preview also has no continuous tone. Android closes finite cue tracks after their tails and performs no continuing audio rendering while idle.

Source commit `25ce7cc95ad627546800592fecf1ccd105f21a98`; successful run https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/43247759037. All six API 36 tests passed, including real cue playback, zero active tracks after the chime, stable rendered-frame count during subsequent idle, mute/reopen/unmute, pause/resume, complete game playthrough, navigation, saves and rotation. Installed as an update over 0.1.1 using the same signer. APK v2 signature verification passed. Physical 0.1.2 listening confirmation remains David’s next test.

Public download independently CRC/checksum verified: `Nova_Pocket_Holodeck_v0.1.2.apk`, 38,563 bytes, SHA-256 `90981f13d12c347f7c6d8531c0573b4b0c0f6403259e4d7fe87610e29458f525`.

## 0.2.0 — worlds arrive without reinstalling the host

Successful build/test/release run: https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/43258163758

Tested source commit `8d3d03e0bf1c93cc2a389d1cc163d68f1c7f480b`. Browser playthroughs passed for Starwell and Lanternwake. API 36 emulator checks: one prior-APK migration test and eight full-suite tests, all passing.

- Installed 0.1.2, played and saved a real first star, then installed 0.2.0 with the same signer. Saved Starwell progress survived.
- Installed the host before publishing Lanternwake. The native Discover screen fetched the actual signed GitHub catalog and downloaded/installed the real separate package. Lanternwake is absent from host assets.
- Native touch steering collected all nine lanterns across three passages and reached the ending. After initial online download, airplane mode was enabled and Wi-Fi/data disabled for the remaining voyage, exit/re-entry, world update and rollback. Local saves survived both data-only version changes; the APK lastUpdateTime did not change.
- Separate per-world virtual HTTPS origins, absent unrestricted Android JS bridge, rejected tampered catalog signature, traversal ZIP and corrupt package hash. Failed package attempts left installed content intact.
- All six previous host/Starwell tests still pass: real touch completion, navigation, saves, settings, rotation/tap alternative, pause/resume, sandbox restrictions and finite native audio with idle silence.
- Android exposed a real drag-release issue: the final finger-up coordinate was missed when no final move event arrived. Lanternwake 1.0.2 applies that coordinate. The same native playthrough now passes. Frame-time catch-up uses short collision-safe substeps.
- Original signer recovery was actually exercised locally and through an encrypted GitHub Actions exchange. Cloud restore run 43256388458 succeeded and validated the original certificate fingerprint. Private recovery material remains in the owner’s private recovery kit; public source/assets contain encrypted data and public certificates only.
- APK v2 signature verification passed. Public APK independently downloaded, ZIP CRC checked, hash matched GitHub’s published asset digest. APK: `Nova_Pocket_Holodeck_v0.2.0.apk`, 56,999 bytes, SHA-256 `75b3a0a042c2a66117f57ebc0ac76c920e27b51815e18e04782ba29186f41970`.
- Main remained `2e6f0e3fee0e5a36f54429eada53e3d5fa01655d`; unrelated projects/store were untouched.

These results are emulator/automated verification, not physical BLU testing of 0.2.0. David confirmed the preceding quiet-background 0.1.2 game works well. The emulator cannot establish physical speaker output, battery use or smoothness on the BLU.

### Independent content publishing proof

Run https://github.com/harbolddavid-wq/nova-starfall-defense/actions/runs/43259889595 passed the Lanternwake browser playthrough, verified unchanged immutable package bytes and published a newly signed catalog. It was the only workflow run for the content-publishing commit; no APK build ran. Public Lanternwake 1.0.2 package: 8,329 bytes, SHA-256 `c73de410667807829f3a18a966fc5a439f5f243390d0b06c57aef266bd763a62`. Its catalog signature was independently verified against the original APK certificate, and package hash/ZIP CRC passed.

Final API 36 logs: zero FATAL EXCEPTION entries, zero app ANRs and zero destroyed-WebView warnings. Discover, downloaded Library, live voyage, airplane-mode ending and restored-save screenshots were inspected. Evidence is included in the 0.2.0 release. No physical BLU performance or audio claims are inferred from those screenshots or emulator results.
