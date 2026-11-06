# Nova Card Collection Android build candidate 0.1.2

A working offline Canasta prototype with touch-first interaction and configurable AI players.

## What's included
- Prominent **Table & Cards** design gallery (19 card backs, 10 table styles).
- One original character drawing supplied by David and three Starlight theme-art-derived decorative backs, sourced from the family's existing artwork. Keep original art attributed to Desi/Starlight. Do not repurpose as a generic stock asset.
- Tap stock to draw, drag from hand to discard to end your turn, and drag a valid group to the team meld area.
- Tap each AI player portrait to set independent skill, personality, and risk-taking. Settings persist offline.
- Slower visible AI turn/discard pacing.

## Install-test status
The v0.1.2 **test build** uses a separate application ID `site.novasky.cards.preview`, so it installs alongside the older v0.1.1 prototype and does not destroy that game's local save. This is not a production-signed release. Android phone testing remains required.

Original Canasta house rules remain the authority. Some edge cases remain unfinished. The family store is not updated by this branch.

Build on GitHub Actions: Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9, SDK 35, `gradle -p nova-cards-android :app:assembleDebug --no-daemon`.

The source artwork is private family intellectual property, and including it in this game does not grant reuse rights for unrelated projects.
