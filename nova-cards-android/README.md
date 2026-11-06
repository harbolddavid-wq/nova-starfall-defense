# Nova Card Collection 0.2.1
All four approved/playable offline games continue from 0.2.0.
- **Change Games** now has a standalone sticky navigation row with a large, reliable touch target even when the game is scrolled.
- Solitaire stock and all face-down tableau cards now display the same chosen card back as Canasta; designs update immediately when changed, without restarting Solitaire.
- Original gold/purple fantasy dragon-and-playing-cards app icon now appears in the Android launcher (real mipmap resource). Icon derived from original generated artwork.
- Gameplay, balances and house rules remain unchanged.
Validated with phone-sized browser tests: switch games while scrolled on all four games, change Solitaire card backs for stock and tableau immediately, no JS errors. Existing 15 Blackjack rounds, 10 Poker hands with chip conservation, Solitaire undo, and prior Canasta tests passed.

Android v0.2.1 keeps package site.novasky.cardcollection. **Important:** GitHub Actions debug keys are ephemeral, so Android may require uninstalling v0.2.0 to install this APK. A persistent signing key is still needed for future true in-place upgrades. No changes to the official Nova Family Store or Starfall Defense main branch.
