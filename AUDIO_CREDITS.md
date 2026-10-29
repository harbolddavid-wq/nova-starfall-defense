# After Hours audio provenance

The 24-bar, 40-second three-beat score and the original musical cue arrangements were newly composed for this work-in-progress. They were rendered using `tools/compose_after_hours.py`; the script pitch-shifts, mixes, pans and adds short room reflections. The score remains unchanged in the character-sound update. The sling, wood and glass cues are now reworked using the recordings below; `tools/render_character_sounds.py` creates those mixes, the character cues and a short synthesized iron impact. Source samples are included in `tools/samples`, and their original paths and verified Git blob SHA-1 hashes are in `tools/samples/SAMPLE_MANIFEST.json`. Music quality, balance, and looping still require listening on an Android phone; technical measurements are not a listening verdict.

- **Folk harp and strumstick recordings:** Versilian Community Sample Library (VCSL), Versilian Studios LLC, https://github.com/sgossner/VCSL, Creative Commons Zero 1.0, https://creativecommons.org/publicdomain/zero/1.0/ . The recorded pitches mapped in the script follow the measured pitch conventions recorded in the Mothlight source notes.
- **Cello and violin-section recordings:** Versilian Studios / VSCO 2 Community Edition, CC0 1.0, obtained as shortened mono sample derivatives from `lidge-ai/music2-gen` (`instruments/strings/samples`), with exact paths and original-file hashes in the sample manifest. The recordings are further transposed and mixed in this game. Source and notices: https://github.com/sgossner/VSCO-2-CE and https://github.com/lidge-ai/music2-gen/blob/main/THIRD_PARTY_NOTICES.md .
- **Interface cues:** `Sounds/click-b.ogg`, `Sounds/switch-a.ogg`, and `Sounds/tap-a.ogg` from David's user-compiled Kenney collection. The collection includes Kenney CC0 notices; because original pack boundaries were merged, the individual underlying pack identity cannot be reconstructed reliably. Respectful attribution: Kenney, https://kenney.nl/assets . The input ZIP remains separate and unchanged.

Interface source SHA-256: click-b `154991a6d9ac80ae193ebad3de7eacb642821e9e48d801d054aa9d2b1004e8c6`; switch-a `1a4c90fed704b4f4b36d58f481117cfac5cdc25279a9af97d4765b71ff21ed48`; tap-a `f74c1334c0c1ab9ec69f3dd723611a8eb89a2630df6c3760dee3b09413370c83`.

## Character and impact sounds — 0.2.2

- **Reitanna (Freesound), CC0 1.0:** four recorded adult female voice reactions, edited for short gargoyle hit and idle sounds: [ooh](https://freesound.org/people/Reitanna/sounds/323707/), [big gasp](https://freesound.org/people/Reitanna/sounds/343878/), [laugh 87](https://freesound.org/people/Reitanna/sounds/343944/), and [giggle 8](https://freesound.org/people/Reitanna/sounds/343991/).
- **SypherZent, CC0 1.0:** [short whoosh](https://freesound.org/people/SypherZent/sounds/420668/) layered with the existing strumstick sling cue.
- **micahlg, CC0 1.0:** [wood impact](https://freesound.org/people/micahlg/sounds/421929/) layered with the existing cello cue.
- **mccormick_iain, CC0 1.0:** [glass break](https://freesound.org/people/mccormick_iain/sounds/371091/) layered with the original harp cue.
- **Iron:** newly synthesized decaying metallic tones layered with the above CC0 wood-impact recording.

The actual source recordings and original earlier cue renders are retained in `tools/samples/sfx/`. `SOURCE_MANIFEST.json` records each original Freesound link, mirrored source Git blob ID and SHA-256; all listed recordings are CC0 1.0. Modified cues are rendered to short mono 32 kHz Ogg Vorbis, with fades, filtering and level adjustment. Cues and music remain independently muteable. The files have been decoded and event routing tested locally, but balance and character suitability still need a human listening test on the BLU.
