# StreetGym

Dedicated Android training app in the [Android apps suite](https://github.com/justdataplease/android-apps).

600 calisthenics workouts across six levels and 124 animated movements. Basketball now lives in its separate StreetBasketball app.

Fresh Court is the default light theme. Settings > Dark mode selects Night Session and saves the choice. Panels use 24 dp corners, buttons 16 dp corners; layouts support landscape and enlarged text. Training, checked sets and progress stay on the device. Future sessions can be previewed but remain locked. Optional Firebase sign-in appears only when configured, and does not synchronize training progress.

## Build

Use JDK 17 and Android SDK 35. From this repository:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.justdataplease.streetgym.debug.test/com.justdataplease.streetgym.TrainingInstrumentation
```

On Windows, use `gradlew.bat`. Minimum Android version is API 23. The application ID is `com.justdataplease.streetgym`; debug adds `.debug`. Debug APKs use development certificates. Production signing must be supplied separately with the original StreetGym certificate for an existing-installation upgrade.

## Ads and privacy

One native card appears below Today progress. UMP handles consent; Settings > Ad privacy opens available privacy choices. Training works offline and active workouts have no ad placement. AdMob may process device identifiers, IP address and ad interactions; this integration does not add workout content or progress to ad requests. Failed ads collapse without blocking training. Debug builds use Google demo IDs. Release configuration reuses the Toolbox account, with per-app Gradle-property overrides supported.

Both Google native demo units returned an invalid-response SDK error in emulator checks. Served-card appearance and production ad delivery remain unverified. This source push does not publish a production release or establish Play readiness.

## Data and optional accounts

Original gymnastics progress keys are retained, and old basketball preferences are left untouched. The preserved raw research export is historical provenance; normal builds use the bundled curriculum.

Firebase is optional. Put Android client resources in ignored `app/src/debug/res/values/firebase.xml` / `app/src/release/res/values/firebase.xml`, with `google_app_id`, `google_api_key` and `project_id` for the corresponding package. Never put service-account credentials in the app.

## Source and checks

Synced from suite commit `9033a445a37580f8d6fa48dc8f4cdbb942081661`. The suite passed both apps' build/lint and eight Android training/theme scenarios each, restoring test preferences afterwards. The original training assets are retained unchanged. CI builds the standalone app and test APK and runs lint; it does not run an emulator or sign a production release. See `SOURCE-SYNC.json` for source provenance.

## Downloads

Install the signed development APK from [Latest](https://github.com/justdataplease/streetgym/releases/latest). These GitHub builds use the `.debug` package and Google demo ad IDs. They are for sideloading and are not production/Google Play builds.

Version 2.1.0 (code 6) follows the existing v2.0.0 GitHub release, whose APK reported 1.3.0-debug (code 5). The new GitHub APK retains that package and certificate for an in-place update; gymnastics progress keys remain unchanged. Basketball now has its own app.
