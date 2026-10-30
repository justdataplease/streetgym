# Street Gym: Daily Calisthenics

**Street Gym** (on Google Play "Street Gym: Daily Calisthenics"; code, package and folders keep the earlier name: `streetgymnastics`, `com.justdataplease.streetgym`) is a dedicated Android app with 1,000 guided calisthenics workouts across ten levels, 40 belted skills and 181 movements in the current curriculum library. The ten-level curriculum and its supporting code are synchronized from the `sg-placement-points` worktree. The assigned daily workout is for an individual athlete.

Settings > Theme picks one of three looks, all free:
- Concrete & Tape: light, the default.
- Night Park: dark.
- Wheatpaste: light.

Looks that were dropped open as the one they copied; Fresh Court falls back to the default. Older installs with dark mode on open in Night Park.

On first launch with no progress, three questions (push-ups, pull-ups, squats) suggest a start at Level 1 (day 1, 22 or 41), Level 2 or Level 3. The athlete can agree or start from scratch. Training, checked sets, timers and progress are kept on the phone. Plus and Pro can sign in with Google to join the leaderboard, and Pro also saves them to the account; both appear only when a Firebase configuration is supplied (see [Free, Plus and Pro](docs/TIERS.md)).

Training has four tabs: **Levels**; **My workouts** (days saved with the heart at the top right of their overview, and custom days built from every exercise in the program); **Library** (Pro: 36 ready workouts in eight groups, each a whole class with warm-up, cool-down and easier options, written by `tools/build_ready_workouts.py` into `assets/workouts.json`; other tiers see them with the Pro offer); and **Parks** (Plus: 73,303 bundled outdoor workout parks from OpenStreetMap on a map that opens on the athlete's location with the nearest parks in view, or as a list; search by city or park). The map is MapLibre with OpenFreeMap tiles, as in JustMaps: no API key and no per-view cost. Progress has **Skills**, **History** and **Leaderboard**.

A day's overview and the workout's list view carry one safety line ("Keep 2–3 reps in reserve and stop for sharp joint pain. Safety notes ›") that opens the readiness and safety notes of the class.

"We choose the workout. You just train." Today assigns the next session. Future days show only their titles until the preceding day is complete; the next day unlocks immediately. Check individual sets or entire sections without a scroll jump, or explicitly complete a day or the current level. Basketball screens and assets now belong to the separate StreetBasketball app.

## Build and run

From `C:\MyDrive\jdp\android-apps`, using JDK 17+ (Android Studio JBR or `%USERPROFILE%\dev-tools\jdk21`) and Android SDK platform 36:

```powershell
.\gradlew.bat :streetgymnastics:assembleDebug :streetgymnastics:assembleDebugAndroidTest :streetgymnastics:testDebugUnitTest :streetgymnastics:lintDebug
adb install -r streetgymnastics/app/build/outputs/apk/debug/streetgymnastics-debug.apk
adb install -r streetgymnastics/app/build/outputs/apk/androidTest/debug/streetgymnastics-debug-androidTest.apk
adb shell am instrument -w com.justdataplease.streetgym.debug.test/com.justdataplease.streetgym.TrainingInstrumentation
```

The nested project also retains its standalone Gradle wrapper (`gradlew.bat assembleDebug` inside this folder); standalone output names use `app-debug.apk`. The standalone toolchain matches the suite: AGP 8.13.2, Kotlin 2.3.21 and Gradle 8.14.6. Android 7.0/API 24 minimum (Play automatic protection needs it, since 2.8.0), compile/target SDK 36 (Android 16: predictive back through `OnBackInvokedCallback`, edge-to-edge insets). Production application ID: `com.justdataplease.streetgym`; debug adds `.debug`. [Latest signed development APK](https://github.com/justdataplease/streetgym/releases/latest). The GitHub downloads use the `.debug` package and Google test ad IDs; they are not production/Play builds.

### Release build

`.\gradlew.bat :streetgymnastics:bundleRelease :streetgymnastics:assembleRelease` signs with the Play upload key when the git-ignored `streetgymnastics/keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; `storeFile` is relative to this folder) and `streetgymnastics-release.keystore` are present. Without them the release is unsigned, so CI still builds. The key is backed up outside the Google Drive mirror, in `C:\Users\jason\keys\streetgymnastics`; losing it means a key-reset request to Google. Upload certificate SHA-256: `64:1A:1E:96:22:5B:85:0B:C0:A0:A8:E1:20:AB:94:B1:DA:36:43:C6:84:09:50:58:91:73:3E:7B:FE:6D:12:EB`. A release also needs the ignored `app/src/release/res/values/firebase.xml`. Check the bundle with `python -I tools\play\check_package.py <aab>` from the suite root.

## Ads and privacy

Ads are for Free only; Plus and Pro have none. One Google native card at most per screen (the current theme, 24 dp corners, an Ad label and the SDK AdChoices control), in these places:
- **Today:** below the progress card.
- **Training › a level's class list:** after the first three classes.
- **Class overview:** below the exercises.
- **Workout (list view):** after the last section, above Finish. Focus view has none.
- **Progress › Skills:** after the fourth skill ladder (or the last, when a filter shows fewer).
- **Progress › History:** below the totals, above the level breakdown. Finishing a class lands on Progress, so its card is the class-finished one.

A full-screen AdMob interstitial may appear at a natural break, a finished section or leaving a workout, at most once every 5 minutes of foreground use (`AdBreakClock.GAP_MS`, `AdBreakPolicy`). Never on app open, mid-set, while a rest, timer, stopwatch or alarm runs, or over the paywall or the consent form. Background time does not count and missed breaks don't stack. Timer, Parks and the class editor have no ads. Training works offline; ads and configured account sign-in use the network. AdMob can process device identifiers, IP address and ad interactions; the integration does not send workout content or progress. Settings > Ad privacy opens Google's available privacy choices. Consent is handled by UMP before ad requests.

Debug builds always use Google demo IDs. Release builds read Street Gym's own AdMob app (2026-10-30) from the suite's `gradle.properties`: `admob.streetgymnastics.appId`, `admob.streetgymnastics.nativeId` and `admob.streetgymnastics.interstitialId`. They never fall back to Toolbox's `admob.*` IDs; a build without them (standalone CI) uses demo IDs. Canonical native ad code lives in the suite's `shared/nativeads`; run `tools/sync_native_ads.py` after changes. Copies inside the app keep standalone exports self-contained.

Free has Levels 1–3, ads and 3 saved days. **Plus** (one payment, 14.99 on Play) unlocks Levels 4–6, removes every placement, and adds the leaderboard (Sign in with Google and a place on it), 30 saved and custom days, 3 lists of their own in My workouts with 10 days each (beside Favorites, where every saved day goes), level renaming and Parks. **Pro** (a yearly subscription, 29.99 on Play, or Pro for life, one payment of 89.99) adds Levels 7–10, the 36 ready workouts, cloud progress, 300 days and 10 lists of 30; its offer says the yearly plan pays for the servers. Today, Training and the workout overview show the applicable paywall: Plus from Level 4 and Pro from Level 7. Settings shows both offers as short lists with Play's prices and Restore purchases; ownership stays cached offline. **Play Console products, the Firebase project and live checkout tests remain required**; see [Free, Plus and Pro](docs/TIERS.md). Debug and GitHub builds can't buy, so they get Pro; Settings > Simulate tier (debug only) tests Free and Plus.

## Timer tools

The Timer navigation tab contains a countdown with presets, a stopwatch with laps, and a one-time clock alarm. Timing persists independently of workout progress. Background alerts require notification permission; precise delivery also needs the Android Alarms & reminders permission. The alarm screen guides the user to these settings. Stop is available in the app and alert notification. Android force-stop cancels scheduled delivery until the app is opened again.

For emulator integration checks, keep system animations enabled, grant `android.permission.POST_NOTIFICATIONS` on API 33+ and allow the `SCHEDULE_EXACT_ALARM` app operation on API 31+. Tests save and restore their preferences and use only test-APK fixtures for cached purchase ownership. No paid transaction is simulated as a real purchase.

## Progress and account configuration

The original StreetGym application ID and gymnastics preference keys are retained. Existing basketball preferences are left untouched, but are no longer displayed. The GitHub development APK retains the previous GitHub APK's signing certificate and updates its `.debug` package in place. Production-package upgrades would require their own matching certificate.

Firebase is optional. Add ignored `app/src/debug/res/values/firebase.xml` or `app/src/release/res/values/firebase.xml` with `google_app_id`, `google_api_key`, `project_id` and `default_web_client_id` only for the corresponding registered package; enable Google sign-in and deploy `firebase/` (rules and the board function). The full console checklist, fingerprints included, is in [docs/TIERS.md](docs/TIERS.md), "Pro account setup". Never copy service-account credentials into the app. Tests restore the preferences they modify and do not create online accounts.

## Content checks

From this folder, run `python tools/build_curriculum.py` (it writes all 1,000 classes from the plan in `docs/CURRICULUM_PLAN.md`), then `tools/assemble_curriculum.ps1` (every class is authored for Street Gym; the catalog carries no other app's ids or provenance fields), `tools/validate_curriculum.ps1`, `tools/validate_animations.ps1`, `python tools/validate_morning_class.py`, `python tools/validate_progressions.py` and `python tools/validate_progression_motions.py` (the Python checks need Pillow), and copy `generated/curriculum_v2.json` to `app/src/main/assets/curriculum.json`; `tools/package_dist.ps1` runs the whole chain. See `docs/CURRICULUM_METHOD.md` for how the classes are made and `docs/CURRICULUM_PLAN.md` for the program itself.

Exercise cards show the animation followed directly by the exercise instructions and sets. Animations play automatically; the Pause / Slow motion toolbar and repeated movement-guide caption have been removed.

The **Next class** home-screen widget shows the next class (level, class number, title, first step and minutes) beside a small figure looping the class's first movement; a tap opens that class. It never names a day or a date: the athlete trains whenever they choose, and the widget follows the classes done, skipped, the theme and the tier.

Today's opening park scene repeats its daily three-exercise routine, with a brief final skill hold and fade before the next walk-in. Returning to the visible card starts all three exercises again. Android's reduced-motion setting still shows a stationary final pose.
