# Nova Cards — v0.1.4 audit build
Android offline Canasta test APK, compiled from bundled single-file HTML.
Version 0.1.4: single-player partnership opening explicitly audited; show opponent opener, initial point requirement and exact cards, per-meld points and breakdown. 6+6+2 is 30 pts, legal only after a 50+ opener at score 0. All game moves still validated by the same engine.
Package site.novasky.cards.previewv014 installs alongside earlier test APKs so their saves are not overwritten.
27 automated Node tests passed locally, including AI full-round simulation, partner cannot combine opening cards, pickup opening audit. Phone-sized browser interface test passed.
This public branch is only for building the install-test APK; the official Family Store and Starfall Defense main branch are not modified. The APK remains a test build and is not production signed.
