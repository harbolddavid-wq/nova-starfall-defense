# Moonbeam Coloring — Android asset-reuse experiment
An original, offline, touch-first coloring app for Android 11+ (BLU ViewPro 5G Android 16 supported).
This project lives on the isolated experiment/coloring-book-asset-reuse branch; the unrelated Starfall Defense main branch is untouched.

## Features
Five original programmatic line-art pages: Moon Moth, Dragon Egg, Haunted Castle, Magic Garden, Cosmic Cat.
Brush, eraser, flood-fill bucket, Kenney CC0 selectable star stamps, Kronbits CC0 selectable sparkle texture brush, palette, brush size, undo/redo (12 steps), page-local PNG autosave, and JPEG export to Pictures/Moonbeam Coloring through MediaStore.
Source: coloringbook/src/main/java/com/novasky/moonbeam/MainActivity.java. App ID com.novasky.moonbeam.

## Source provenance
See ASSET_PROVENANCE.json for exact Library archive paths, ZIP member names, source SHA-256 hashes, and modifications. These are pre-existing downloaded THIRD-PARTY resources from David's Library, not newly generated images. Kenney's original ZIP was user-combined, so original sub-pack boundaries cannot be reconstructed. Kronbits's original ZIP lacks an embedded license file; its catalog records the author's CC0 listing. No Nintendo art/music/branding is used.

## Install
Use Moonbeam Coloring APK artifact from this branch's GitHub Actions build or saved ChatGPT Library APK. Android may ask to allow installs from this source. Launch Moonbeam, choose Pages, paint, use Stamps or Sparkle, and Save JPG to Pictures/Moonbeam Coloring. Source ZIP is included with CI artifact.

## Limits
Only Android 11+; portrait only. No import, pan/zoom, or color-by-number. Line art is original procedural geometry; not hundreds of picture templates. The first APK is a debug-signed test build, not a production signing-key release. Physical BLU testing remains David's step.
