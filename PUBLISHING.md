# Publishing compatible Holodeck worlds

This standalone project lives only on `work/pocket-holodeck`. Do not merge its root tree over unrelated apps on main. No paid infrastructure is required: standard public GitHub Actions runners and GitHub Releases provide the current delivery route.

## Experience-only delivery

1. Put reviewed, owned or licensed HTML/CSS/JavaScript files under `experiences/<id>/`.
2. Use `world.json` to declare id, title, subtitle, description, runtime 1, entry HTML, version, integer versionCode and an explicit files array. Follow Lanternwake’s example. Use relative local URLs; no external scripts, network, frames, workers, inline scripts or Android bridge.
3. Increment versionCode whenever any package bytes change. Each `<id>-<versionCode>.zip` is immutable; the publisher refuses replacement with different bytes. Keep runtime and origin-scoped save keys compatible with existing saves.
4. Commit the world change to this branch. `publish-holodeck-worlds.yml` runs the Lanternwake browser playthrough, packs deterministic ZIPs, hashes their inventory, signs the catalog, uploads packages first and catalog last. It never assembles an APK. The host’s Discover refresh then offers download/update. Existing downloaded worlds remain usable offline.

Host changes use `[host]` in the commit message. Their build/test workflow publishes the current content after installing the candidate on the emulator, before testing actual delivery. A host rebuild is required only when native capabilities/runtime compatibility change.

## Keys and recovery

The installed APK certificate anchors catalog trust. Keep the exact original signer; generating another key would break Android updates and catalog trust. Workflows restore `pocket-holodeck-candidate-signer-v2` and refuse silent replacement. The `holodeck-recovery` release contains only an AES-256-GCM/RSA-OAEP encrypted signing-key backup and public metadata/certificates.

David’s private `Nova_Pocket_Holodeck_Owner_Recovery_Kit.zip` contains the separate recovery private key and instructions. It is stored privately for future Nova sessions. It must never be committed, added to a public release, logged or shared. The included assistant-operated encrypted exchange lets Nova restore the original signer without asking David to configure tools, copy secrets or use a computer. A real round trip recovered the signer locally and again inside Actions, with the original certificate fingerprint checked.

## Current limits

Publishing currently uses the connected GitHub workflow, with no live account backend or ChatGPT publishing plugin. Compatible package downloads/updates are already independent of APK builds. The host is a sideloadable personal-project candidate; physical-device performance and speaker output remain separate from emulator checks.
