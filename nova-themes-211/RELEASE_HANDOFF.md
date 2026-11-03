# Nova Themes 2.0.11 — named pages, per-icon management
Date: 2026-11-03

**App:** com.novasky.themes, versionCode 211, versionName 2.0.11.

**Branch:** experiment/nova-themes-named-pages-211-20261103

**Implementation:** `nova-themes-211/apply_named_pages.py` applies changes to the exact MP4 2.0.10 candidate code from `nova-mp4-bridge/code.zip.b64`. Preserves original Home / Nova Apps keys, clock, dock, folders and direct Lockscapes MP4 code.

**Features:** up to twelve named pages, swipe left/right, page picker and create/rename/delete, persistent per-page shortcuts/order/folders, moving apps between pages, held-icon context menu (uninstall via Android confirmation, App info, move, folder, customize and remove shortcut), drag within a page, backup/restore of the new page data.

**Build:** GitHub Actions run 41715648011 compiled unsigned code with Android SDK 35. Host checks: NavigationGestures 19/19 and GestureGate 14/14. An Android release APK was assembled locally with 32 exact theme assets from the known signed 2.0.9 APK and signed using the pre-existing **private** Nova Themes 2 certificate (not uploaded to GitHub).

**APK SHA-256:** `95d70fc80fc016ed32147b6572039186289ff0dad256c34a92155a14fe870b96`

**Certificate SHA-256:** `1e3b8514b100a0026576cb63b4c8126c0a33e5e727fa1d1e4a025b1da19bd11d` — checked as identical to 2.0.9's certificate.

**Library:** `/Nova Themes 2/Nova_Themes_2.0.11_Named_Pages_SIGNED.apk` and `/Nova Themes 2/Nova_Themes_2.0.11_Named_Pages_SOURCE.zip`.

**Safety:** DO NOT uninstall or clear Nova Themes to test upgrade. Install 2.0.11 over existing app; report installer error instead of clearing data. No physical BLU or live emulator test has occurred yet, so behavior and preference retention need user confirmation.

**Not included:** further theme artwork (secondary priority), Android widgets, automated app updates.

**Security:** never commit keystore, password, signed original artwork, or private backups to this repo.
