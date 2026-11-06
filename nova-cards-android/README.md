# Nova Cards — 0.1.5 automatic hand organization
Offline Canasta install-test build. Cards in the player's hand are automatically sorted A, K, Q, J, 10 down through 3; Jokers and 2s grouped as wilds at the end. Card IDs, game rules, score and undo state are unchanged. Gaps between rank groups and preserved horizontal scroll support large hands after picking up the discard pile.
Tested: 31 Node engine/sorting tests passed, mobile Chromium 46-card hand with consecutive groups, no lost cards, preserved scroll while selecting, and exactly one discard on double tap.
Version 0.1.5 uses a distinct test package identity site.novasky.cards.previewv015, so it installs alongside all earlier test apps and does not erase their progress.
This branch builds test APKs only; the official Nova Family Store is not modified, and a production-signed release still requires more testing.
