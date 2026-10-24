# Street Gymnastics

Dedicated Android app with 600 guided calisthenics workouts across six levels and 138 animated movements. Copied from the owned StreetGym source into the Android suite; the original project remains unchanged.

Fresh Court is the default light theme. Settings > Dark mode selects Night Session and remembers it independently in each app. Training, checked sets, timers and progress remain local. Optional Firebase account access appears only when client configuration is supplied; it does not synchronize progress.

"We choose the workout. You just train." Today assigns the next session. Future days show only their titles until the preceding day is complete; the next day unlocks immediately. Check individual sets or entire sections without a scroll jump, or explicitly complete a day or the current level. Basketball screens and assets now belong to the separate StreetBasketball app.

## Build and run

From `C:\MyDrive\jdp\android-apps`, using Android Studio JBR and Android SDK 35/36:

```powershell
.\gradlew.bat :streetgymnastics:assembleDebug :streetgymnastics:assembleDebugAndroidTest :streetgymnastics:testDebugUnitTest :streetgymnastics:lintDebug
adb install -r streetgymnastics/app/build/outputs/apk/debug/streetgymnastics-debug.apk
adb install -r streetgymnastics/app/build/outputs/apk/androidTest/debug/streetgymnastics-debug-androidTest.apk
adb shell am instrument -w com.justdataplease.streetgym.debug.test/com.justdataplease.streetgym.TrainingInstrumentation
```

The nested project also retains its standalone Gradle wrapper (`gradlew.bat assembleDebug` inside this folder); standalone output names use `app-debug.apk`. The standalone toolchain matches the suite: AGP 8.13.2, Kotlin 2.3.21 and Gradle 8.14.6. Android 6.0/API 23 minimum, compile/target SDK 35. Production application ID: `com.justdataplease.streetgym`; debug adds `.debug`. [Latest signed development APK](https://github.com/justdataplease/streetgym/releases/latest). The GitHub downloads use the `.debug` package and Google test ad IDs; they are not production/Play builds.

## Ads and privacy

Google native cards appear below progress on Today and at the end of workout sections. It uses the current theme, 24 dp corners, an Ad label and the SDK AdChoices control. After at least ten minutes of foreground use, an available full-screen native ad may appear at a section completion or when leaving a workout. Close is available immediately. Active independent timers, stopwatches and ringing alerts defer the break. Training works offline; ads and configured account sign-in use the network. AdMob can process device identifiers, IP address and ad interactions; the integration does not send workout content or progress. Settings > Ad privacy opens Google's available privacy choices. Consent is handled by UMP before ad requests.

Debug builds always use Google demo IDs. Release builds read `admob.streetgymnastics.appId` and `admob.streetgymnastics.nativeId`, falling back to the existing Toolbox `admob.appId` / `admob.nativeId` settings, then demo IDs if neither exists. An optional `admob.streetgymnastics.fullscreenNativeId` overrides the full-screen native placement; otherwise it uses the same native unit. Canonical ad code lives in the suite's `shared/nativeads`; run `tools/sync_native_ads.py` after changes. Copies inside the app keep standalone exports self-contained.

Settings > Remove ads offers a one-time 9.99 purchase and Restore purchase. Confirmed ownership disables every placement and remains cached offline. The actual currency and price come from Google Play. **Play Console product activation and a live checkout test remain required**; see [purchase setup and acceptance](docs/REMOVE_ADS.md). Debug downloads do not claim production purchasing support.

## Timer tools

The Timer navigation tab contains a countdown with presets, a stopwatch with laps, and a one-time clock alarm. Timing persists independently of workout progress. Background alerts require notification permission; precise delivery also needs the Android Alarms & reminders permission. The alarm screen guides the user to these settings. Stop is available in the app and alert notification. Android force-stop cancels scheduled delivery until the app is opened again.

For emulator integration checks, grant `android.permission.POST_NOTIFICATIONS` on API 33+ and allow the `SCHEDULE_EXACT_ALARM` app operation on API 31+. Tests save and restore their preferences and use only test-APK fixtures for cached purchase ownership. No paid transaction is simulated as a real purchase.

## Progress and account configuration

The original StreetGym application ID and gymnastics preference keys are retained. Existing basketball preferences are left untouched, but are no longer displayed. The GitHub development APK retains the previous GitHub APK's signing certificate and updates its `.debug` package in place. Production-package upgrades would require their own matching certificate.

Firebase is optional. Add ignored `app/src/debug/res/values/firebase.xml` or `app/src/release/res/values/firebase.xml` with `google_app_id`, `google_api_key` and `project_id` only for the corresponding registered package; enable email/password authentication. Never copy service-account credentials into the app. Tests restore the preferences they modify and do not create online accounts.

## Content checks

From this folder, run `tools/assemble_curriculum.ps1`, then `tools/validate_curriculum.ps1`, `tools/validate_animations.ps1`, `python tools/validate_morning_class.py`, `python tools/validate_progressions.py` and `python tools/validate_progression_motions.py` (the Python checks need Pillow), and copy `generated/curriculum_v2.json` to `app/src/main/assets/curriculum.json`; `tools/package_dist.ps1` runs the whole chain. The October 2026 coaching revision changed the authored classes, the daily practice timetable and the movement library; see `docs/CURRICULUM_METHOD.md` for what changed, source provenance and training boundaries.

Exercise cards show the animation followed directly by the exercise instructions and sets. Animations play automatically; the Pause / Slow motion toolbar and repeated movement-guide caption have been removed.
