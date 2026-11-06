# Nova Card Collection 0.2.2 — Android-safe navigation
The real cause of the top controls missing touches was a target-SDK-35 WebView drawing behind Android's status bar/cutout on Android 15/16.
Native MainActivity now measures status bar, display cutout, navigation bar, and side insets and physically pads the WebView's container. The WebView itself is no longer in the system's touch region.
Inside the page, neither Change Games nor Table & Cards is in the branding header. Both share a large, sticky navigation row underneath it; on phones the two controls sit side-by-side with touch targets 52px tall.
The Learning Lab remains accessible inside Canasta's side controls. Gameplay logic, poker chips, deck customization, solitaire card-back synchronization, and app icon are unchanged.
Browser smoke test: both buttons tappable in all four games despite a simulated 34px status bar consuming touches; Change Games clickable after scrolling; Solitaire stock back still updates; no JS errors. Native compilation via GitHub Actions pending in this commit.
Package site.novasky.cardcollection, versionCode 22. This GitHub Actions install-test build uses an ephemeral debug signing key and may require uninstalling v0.2.1 before installation. Do not publish as production release until real phone test.
