# Nova Card Collection Android build candidate 0.1.1

Offline Canasta game is bundled inside a minimal native Android WebView wrapper. No internet permission, external services, or subscriptions needed to play.

**Status:** Android source candidate, not yet verified as a working APK. Some house-rule edge cases and AI strategy features from the game handoff are still unfinished. This build must NOT be listed in the Family Store as a final game before real phone testing.

Build: Java 17; Gradle 8.9; Android Gradle Plugin 8.7.3; SDK/platform 35.

```sh
gradle :app:assembleDebug --no-daemon
```

The debug APK is for install testing only. Production releases need a private, persistent signing key to ensure upgrades work. Do not expose keys or credentials in the public repository.

The official Family Store remains the Hatchable project at https://nova-family-store.hatchable.site/; this branch does not modify it.
