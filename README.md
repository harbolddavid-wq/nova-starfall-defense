# Nova Family Store — Portal APK

A **new**, lightweight native Android app that displays a custom starry lavender welcome screen and automatically opens https://nova-family-store.hatchable.site in the phone's normal browser after about 1.8 seconds.

- Android package: `com.novasky.familystore.portal`
- Version: 1.0.0, code 1
- No permissions, accounts, trackers, or embedded credentials.
- Browser owns HTTPS downloads and APK install prompts, exactly like the tested website.
- This source is on a dedicated branch. Original Nova Starfall Defense remains on main.
- Built with GitHub Actions public standard runners (free), Android SDK 36 / build-tools 35.
- First APK is debug signed. Sign with a stable private release key before promising an in-place update to this launcher.

**Source edits:** change `STORE_URL` in MainActivity.java only if the store URL changes. The storefront itself can update without changing this launcher.
