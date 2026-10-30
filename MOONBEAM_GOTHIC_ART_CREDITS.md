# Moonbeam Coloring 0.1.1 — Gothic artwork credits and transformation notes

## Why this art is different
David's five original pages are preserved in the application. Five new pages are composed from properly licensed third-party **SVG source artwork** (not crude circles): Dragon’s Reliquary, Gargoyle Cathedral, The Bone Crown, Raven’s Omen, and the surprise The Thirteenth Door. Their decorative stained-glass frames, filigree, leaves, stars, placements and layout are original Moonbeam work.

## Third-party original source and license
- Original repository: https://github.com/game-icons/icons
- Pinned source commit: `82d948812bfe3f269ef8f731dcdb07b08160edc4` (2026-04-23)
- Official icon catalog: https://game-icons.net/
- Original license file: https://github.com/game-icons/icons/blob/82d948812bfe3f269ef8f731dcdb07b08160edc4/license.txt
- License: Creative Commons Attribution 3.0 Unported, https://creativecommons.org/licenses/by/3.0/
- **Icons made by Lorc and Delapouite, available on https://game-icons.net.** Modifications: converted white silhouette SVGs into line-art outlines, combined multiple icons with newly designed gothic borders, resized and composited into PNG coloring pages.
- Source SVGs and license are downloaded by `scripts/build_moonbeam_pages.py` into `art_sources/game_icons/` and included in the complete source artifact. The machine-readable `art_sources/game_icons/DOWNLOADED_SOURCE_MANIFEST.json` records each exact URL, SHA-256, author and adaptation.
- Source files are not from David's existing Library. Existing Kenney CC0 stamps and Kronbits CC0 sparkle textures remain separate, unchanged and functional.

## Original source assets used by page
- Dragon’s Reliquary: Lorc `dragon-head.svg`, `dragon-breath.svg`, `pentagram-rose.svg`, `evil-moon.svg`.
- Gargoyle Cathedral: Delapouite `gargoyle.svg`, `castle-ruins.svg`, `bat.svg`; Lorc `evil-moon.svg`.
- The Bone Crown: Lorc `daemon-skull.svg`, `crown-of-thorns.svg`, `rose.svg`; Delapouite `dagger-rose.svg`.
- Raven’s Omen: Lorc `raven.svg`, `evil-moon.svg`, `rune-stone.svg`; Delapouite `graveyard.svg`.
- The Thirteenth Door: Lorc `magic-portal.svg`, `rune-stone.svg`; Delapouite `floating-ghost.svg`.

## Source-to-pixels build
`python3 -m pip install pillow cairosvg && python3 scripts/build_moonbeam_pages.py` generates `coloringbook/src/main/assets/pages/page_5.png` through `page_9.png` and `Moonbeam_Gothic_Pages_Preview.jpg`. All five PNGs must be physically embedded in the Android APK under `assets/pages/`. The pages are line art; flood fill operates on pixel boundaries.

## Undo/Redo root cause (source diagnosis)
Version 0.1.0 used `paintLayer.copy(Bitmap.Config.ARGB_8888,false)` to snapshot history. The false argument creates an **immutable** Bitmap. On Undo or Redo, `swap` calls `new Canvas(paintLayer)` with that immutable bitmap, which Android rejects. That explains the observed exit to home; real-device stack trace was not collected. Version 0.1.1 changes history snapshots to mutable bitmaps, updates enabled/disabled button states, and tests undo/redo across tools.

## Update safety
Original 0.1.0 APK was signed by an ephemeral GitHub Actions debug keystore; the signing private key is not in the saved source or artifact. A new debug build cannot be assumed to update that installation. **Do not uninstall 0.1.0 or lose existing drawings.** The new test build uses `com.novasky.moonbeam.repair` so it can be installed safely *beside* the original, but it does not automatically import the original's saved pages. A future true in-place update requires the original signing private key, or a deliberate migration plan.
