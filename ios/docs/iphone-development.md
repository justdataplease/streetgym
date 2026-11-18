# Street Gym on iPhone: development plan

Written 3 Nov 2026, updated 18 Nov 2026.

- **Phase 1 is done** (2.11.5): the engine lives in the shared Kotlin Multiplatform module `android-streetgym/shared/`, and the Android app runs on it.
- **Phase 2 has started** (18 Nov 2026): this folder, `ios-streetgym/`, is the iPhone project. Today, the class page and training a class (the focus workout, the rest timer, finishing) are built on the engine, in the three themes. They build, pass their tests and are drawn to pictures on Windows. **Nothing has been compiled for iPhone yet**: that needs a macOS runner (the CI file is written, `ci/ios-ci.yml`, and has not run).

For background on every suite app, see the suite's `IOS-PORTING-REPORT.md`.

## Approach

- **No Mac.** Code is written on the Windows PC. GitHub's macOS runners compile the app, run it in the iPhone Simulator and return screenshots. Signed builds also go to TestFlight from there.
- **CI runs in the public `justdataplease/streetgym` repo**, where macOS minutes are free. The private suite repo would be billed for them.
- **One engine.** Curriculum, progress, placement, points, skills and ranks, effort and the tiers are in `shared/`. Android and iPhone both use it and load the same `curriculum.json`, `workouts.json` and `exercise_animations.json`, so day ids, points and ranks always match.
- **Screens in Compose Multiplatform (CMP).** The iPhone screens are new Kotlin code. Their module gets a desktop target, so screens can be run and screenshotted on Windows without a CI round trip.
- **Android keeps its Views.** Moving Android to the CMP screens is a separate decision for later.
- **Swift only where Apple requires it:** StoreKit 2, Google ads, and the app entry point.

## The engine today (`android-streetgym/shared/`)

Module `:shared` in the standalone repo and in this project, `:streetgymnastics-shared` in the suite. Plugins: `org.jetbrains.kotlin.multiplatform` and `com.android.kotlin.multiplatform.library` (AGP 9.4.1, Kotlin 2.3.21). The targets are Android, `iosArm64`, `iosSimulatorArm64` and, since 18 Nov 2026, the JVM (`jvm()`), which lets the iPhone screens run in a window on Windows. The iPhone targets compile only on a Mac; `kotlin.native.ignoreDisabledTargets=true` keeps Windows and Linux builds quiet about them.

**Everything is in `commonMain`, in the same packages as before**, so the Android app's imports didn't change:

| Package | Files | What it does |
|---|---|---|
| `data.model` | `Curriculum.kt` | The catalog: levels, classes, exercises, sets, `LocalizedText` |
| `data` | `CurriculumJsonParser.kt` | Reads `curriculum.json` and `workouts.json` into the catalog |
| `data` | `Clock.kt` | `epochMillisNow()`, for the default times |
| `data.skills` | `SkillBook.kt`, `SkillCurriculumReader.kt`, `SkillStandards.kt`, `SkillSwitches.kt` | The 40 skills, ranks 1–5, targets and credits, skill switches |
| `data.equipment` | `Equipment.kt` | The equipment switches: the library's equipment tags read as seven pieces (pull-up bar, low bar, parallel bars, box, wall, pole, band), and what a spot without them leaves out |
| `data.progress` | `ProgressRepository.kt` | The progress interface, `WorkoutSession` and `ActiveWorkout` |
| `data.progress` | `Placement.kt`, `NextClass.kt`, `LevelReplay.kt`, `Intensity.kt`, `ExamLog.kt`, `ProgressMerge.kt` | The strength test, next class, repeats, easier/harder, exams, cloud merges |
| `data.progress` | `Streak.kt` | The streak (workout days strung together with at most one rest day between two of them, the best one, lit today or not, whether today may be the rest day) and the week row with its rest days, over local days counted from 1970 |
| `data.points` | `Points.kt` | Points per set and rank, all-time and month totals (UTC months without a platform calendar) |
| `data.effort` | `WorkoutEffort.kt` | Easy/Moderate/Hard "for you" |
| `data.library` | `WorkoutLibrary.kt`, `ExerciseLibrary.kt` | Saved days, custom days, lists, ready workouts and their JSON codec |
| `billing` | `Tier.kt`, `LevelAccess.kt` | Free / Plus / Pro and which levels each opens |
| `ui` | `WorkoutFlow.kt` | A class as ordered sets with the keys both phones save, what counts (30% of the time), points per class, and the notes of a movement and a set. Moved from the Android app on 18 Nov 2026; a sorted map and `Map.merge` were rewritten in plain Kotlin |
| `json` | `Json.kt` | The part of org.json the engine uses (same names and lenient reading), on kotlinx.serialization |

**Rules for shared code:**
- `commonMain` uses no Java or Android API. `./gradlew :streetgymnastics-shared:compileCommonMainKotlinMetadata` (suite) or `:shared:compileCommonMainKotlinMetadata` (standalone, and this project) checks it on Windows. The Android compile alone would let `java.*` through, and so does the desktop one: `WorkoutFlow.kt` compiled for the desktop window with two Java calls in it, and only this check caught them.
- JSON goes through `com.justdataplease.streetgym.json`, never `org.json`. `SharedEngineTest` pins it to org.json both ways, so data stored before 2.11.5 reads back unchanged.
- Times are epoch milliseconds; UTC dates come from `UtcDate` in `Points.kt`.
- The 305 unit tests stay in the Android app (`app/src/test`) and test the shared classes from there. `DayIdsTest` must pass untouched.

**Still Android-only, to port or rewrite with the iPhone app:**

| Where | Why it stayed |
|---|---|
| `data/progress/HistoryCalendar.kt` | The month grid uses `java.util.Calendar` in the phone's time zone; port to kotlinx-datetime |
| `data/progress/StreakDays.kt` | Turns the history's times into local days for `Streak` with `java.util.TimeZone`, and finds the next local midnight; port with the calendar |
| `timer/TimingState.kt` | Same, for the timer's day boundaries |
| `ui/ParkStory.kt` | Pure logic for the park scene, but uses `TimeZone`; move with the park scene |
| `ui/ExerciseAnimationView.kt`, `ui/FrontView.kt` | The line man: pose reading and projection maths are mixed with Canvas drawing; split them, move the maths, redraw in Compose `DrawScope` |
| `data/progress/SharedPreferencesProgressRepository.kt`, `data/library/LibraryRepository.kt`, `data/CurriculumRepository.kt` | Storage and asset reading; the iPhone app writes its own (NSUserDefaults, the bundle) behind the same interfaces. Its progress store (`composeApp`, `data/StoredProgress.kt`) writes sessions, the class in progress and exams with the same JSON names as Android, pinned by a test. **Next engine step:** move that codec into `shared/` and have Android use it too, so one piece of code writes both; it needs an Android release with the stored data checked both ways |
| `ui/HomeActivity.kt` (requestStart, askEffortAndFinish, switchEasier, measureOf, skillMovement, sections), `ui/FocusWorkoutView.kt` (position, skips, timed sets) | The rules of training a class, mixed with views. The iPhone has them once more in `composeApp`, `data/Training.kt`, calling the engine for every count. **Engine step:** move them into `shared/` (a class's four sections and their keys, what finishing saves, the focus position, the timed set's rounds), so both phones run one copy |
| `timer/TimingState.kt` (`clockText`) | Pure, but uses `String.format`; the iPhone's copy is in `ui/DisplayText.kt`. Move with the timer's day boundaries |
| `leaderboard/`, `analytics/`, `account/` | Firebase; they wait for the account (see below) |
| `data/parks/` | Parks map; waits for MapLibre iOS |
| `billing/Entitlement.kt`, `billing/TierBilling.kt` | Play Billing; StoreKit 2 on iPhone |

## First iPhone version (1.0)

| In | Later |
|---|---|
| Today, Training, every class and workout flow, rest timer | Account (Google + Sign in with Apple) |
| Line-man animations, park scene | Cloud backups and restore, progress sharing |
| Skills and medals, skill switches | Leaderboard and usernames |
| Strength test (placement) | Next-class widget (WidgetKit) |
| History (calendar and the month's classes), the streak on Today | Parks map (MapLibre iOS) |
| Ready workouts with filters, Timer tab | Progress analytics upload |
| Themes, Settings, safety text | Android Pro unlocking on iPhone |
| Free / Plus / Pro through StoreKit | |
| AdMob native and full-screen ads, consent, ATT | |

**Why the account waits:**
- Offering Google sign-in obliges Apple's sign-in beside it (guideline 4.8).
- Public usernames need report, block and filtering (guideline 1.2).

**Pro on iPhone 1.0** is levels 7–10 and ready workouts. Its texts must not promise cloud backups until those exist.

## Project layout

Since 18 Nov 2026 the suite has one folder per phone (the owner: "ios versions in folder ios-…, rename existing to android"):

| Suite folder | Standalone repo | What it holds |
|---|---|---|
| `android-streetgym/app/` | `app/` | The Android app, on the engine. |
| `android-streetgym/shared/` | `shared/` | The engine, used by both. |
| `ios-streetgym/composeApp/` | `ios/composeApp/` | The iPhone screens in Compose Multiplatform, with iPhone framework targets and a desktop target for Windows. |
| `ios-streetgym/iosApp/` | `ios/iosApp/` | `project.yml` for XcodeGen (no hand-edited `.pbxproj`), the Swift entry point hosting the Compose view controller, `PrivacyInfo.xcprivacy`, the icon, and the Simulator tour. StoreKit and the ads bridges come here in phase 4. |
| `ios-streetgym/ci/ios-ci.yml` | `.github/workflows/ios-ci.yml` | The checks on GitHub's runners. |

`ios-streetgym` is its own Gradle project (`settings.gradle.kts` finds the engine in `../android-streetgym/shared`, or `../shared` in the standalone repo).

**One source for both phones.** The iPhone app keeps no copy of the Android app's files. At build time it reads from the Android app: `curriculum.json`, `workouts.json` and `exercise_animations.json`, the fonts, the tab icons, and every `strings*.xml` (as Compose resources; two things are rewritten on the way in, a bare `%d` to `%1$d` and Android's `\'` to `'`). A text or a class changed for Android is changed for iPhone.

**On Windows** (JDK 21; `ANDROID_HOME` set, since the engine also has an Android target):

```
cd ios-streetgym
.\gradlew.bat :composeApp:run            # the screens in a window, at iPhone size
.\gradlew.bat :composeApp:screenshots    # a PNG of every screen in composeApp\build\screenshots
.\gradlew.bat :composeApp:desktopTest :composeApp:compileCommonMainKotlinMetadata
```

Ignore `DerivedData`, `xcuserdata`, `*.xcuserstate`, the generated `.xcodeproj` and `iosApp/StreetGym/Info.plist` (XcodeGen writes it).

## Phase 2: iPhone shell and simulator CI

Done on 18 Nov 2026, checked on Windows:

- `composeApp` shows **Today** from the engine (the brand card, the streak's flame and week, the class the sequence offers with its level header, Easier / Normal / Harder, Complete day, progress) and the **class page** (every exercise of the warm-up, skill practice, strength work and cool-down with its sets), in Concrete & Tape, Night Park and Wheatpaste. Sizes, colours, fonts and the per-theme shapes are ported from the Android views, including the room Android leaves round a headline (its font padding).
- Progress is saved (UserDefaults on iPhone), so Complete day moves the sequence on and stays.
- **Training a class** (18 Nov 2026), ported from Android's focus view (`FocusWorkoutView`, `SetDone`, and HomeActivity's start, rest, finish and abandon): Start workout on Today and on the class page opens the class on its first open set; Resume after closing the app lands on the same set. One set per screen: the strip (section, Finish, the class's sets drawn per theme, "Set 7 of 27"), the exercise's panel, its name and Details, the dose, the tempo, "Did more or fewer?", the easier option, then Back, Done · Next or Start N s, Skip. A timed set counts down; per-side holds run two rounds. The rest after a set fills the screen with its countdown, +15 s and Skip rest. The summary offers Finish workout, Back, the skipped sets, and End session. Finishing asks first when sets are left, then "How hard did that feel?" (RPE 1–10), saves the class and returns to Today with "Workout completed · +N points · 1 day streak".
- What finishing saves comes from the engine (`WorkoutFlow.counted`, `credits`, `doses`, `points`; `Streak`): only exercises that ran 30% of their time count. Ticks (with their times), logged numbers and easier options are saved at once; skips, a running countdown and the rest last while the app runs, as on Android.
- The timer logic (`data/Training.kt`) runs on an injected clock. What a workout asks of the phone is `WorkoutDevice`: on iPhone the screen stays on (`idleTimerDisabled`); the sounds are left to do (below).
- Dialogs, the Details sheet and the note after finishing are drawn over the whole screen by the shell (`ui/Overlay.kt`), in each theme's dialog style.
- 25 tests: the progress store and the streak (common), the Android texts, Today and Complete day, Harder, and training over the real program (start, ticks with their times, the rest stepped by the clock, timed sets, finishing with the engine's points and streak, too-fast ticks earning nothing, End session, resuming after the store is read again, skips, the easier option, the countdown and points texts), plus two tests that train the first class by taps on the real screens.
- `iosApp` hosts it: the Swift entry point, the XcodeGen project, the privacy manifest, the 1024 px icon, and a Simulator tour that saves a picture of Today and of the workout's first set.

Still to do in this phase:

- **The first macOS run.** Push `ios/` to the standalone repo and let `ios-ci.yml` run: it compiles `shared/` and `composeApp` for `iosSimulatorArm64` (never done so far; any call that is not there on iPhone shows up here), builds the app with Xcode and runs the tour. No Apple account is needed.
- Not in the app yet: the park scene on the brand card, the strength test for a new athlete, the streak's sheet, and the other four tabs (after a class Android opens Progress; the iPhone returns to Today until Progress exists).
- Weeks start on Monday until the phone's region is read; Today's Easier / Harder pick is forgotten when the app restarts (a class in progress keeps its own).

From here on, most checks happen on Windows and CI confirms on iPhone.

## Phase 3: screens

**Rebuild in CMP**, from about 14,000 lines of Android Views:
- Home and Today (done in phase 2)
- Training (levels, workout lists), workout overview (the class page is done)
- Focus workout, rest timer, effort dialog (done on 18 Nov 2026, see phase 2); still to come for training:
  - **the list view** (Android's other workout view, with its Focus · List switch on the class page and in the title bar). Android ends a session there; until it exists, End session sits at the end of the focus summary
  - **the sounds**: `WorkoutDevice.restOver` and `holdOver` do nothing yet. Android beeps at the end of a rest and of a timed set, and vibrates for the timed set. On iPhone: AVAudio while the app is open, a scheduled local notification while it is in the background (TODO in `iosMain`, `MainViewController.kt`)
  - **exams**: after an exam class Android asks which targets were reached and records them, and may offer to repeat the block (a level replay, which `StoredProgress` doesn't keep yet)
  - skill and equipment switches (sets left out of a class), the ad breaks between sections, the spoken announcements of the rest and the timed sets, and a repeated class (no screen opens a completed class yet)
  - the line man in the exercise's panel: the panel keeps its frame and size, empty, until the animations are ported
- Skills medals (rank shield and Plus/Pro as tags on the corners, segmented bars), skill switches
- History calendar and the month's list
- Ready workouts and filters, Timer tab
- Placement, Settings and themes, the plans panel, the park scene and the animations

**Drawing.** `Canvas`/`Paint`/`Path` code maps closely to Compose `DrawScope`. Port it as directly as possible so the line man looks identical. Compare desktop renders with Android emulator shots for a fixed set of exercises.

**Themes.** Concrete & Tape and the other two free themes become CMP colour tokens. Drawables and decorations (`ThemeDecor`, `ThemeDrawables`) are redrawn in `DrawScope`.

**Strings.** Done in phase 2: a Gradle task copies `app/src/main/res/values/strings*.xml` into `composeApp`'s resources at build time, so the texts have one source (`AppOnDesktopTest` checks an apostrophe, a bare `%d`, numbered places, `&amp;` and a plural). Still to handle when their screens come: `%1$.1f` in one string, `%%` in one, and the `<b>` in `share_body`.

**iPhone details:** the screen is kept awake during a workout (`idleTimerDisabled`, done); play the rest-end sound with AVAudio in the foreground and a scheduled local notification in the background (to do); respect safe areas; support the back swipe (to do).

## Phase 4: purchases and ads (needs the Apple account)

**StoreKit 2, in Swift:**
- Products `plus` (non-consumable, 14.99), `pro_life` (non-consumable, 69.99) and `pro_yearly` (auto-renewing, 29.99, group "Pro").
- Kotlin defines a `Store` interface and Swift implements it. The tier comes from `Transaction.currentEntitlements`.
- A Restore purchases button (Apple requires one).
- The subscription screen shows the price, the period, and links to the terms (Apple's standard EULA) and the privacy policy.
- Test locally with a `.storekit` file in the UI tour, and in the sandbox on TestFlight.

**Google Mobile Ads iOS SDK** through Swift Package Manager:
- UMP consent first, then the ATT prompt (`NSUserTrackingUsageDescription`).
- `GADApplicationIdentifier` and `SKAdNetworkItems` in `Info.plist`.
- The native card ending each page is a `GADNativeAdView` embedded with `UIKitView`.
- Full-screen ads keep the Android rule: every 5 minutes, at the next screen change, never over a screen in use.
- Google's demo ad units in debug builds.

**AdMob:** create the iOS app, a native unit, a full-screen unit and an iOS consent message. `app-ads.txt` already covers it.

## Phase 5: TestFlight and the App Store

- **App record:** bundle id `com.justdataplease.streetgym`, iPhone only (`TARGETED_DEVICE_FAMILY = 1`). Versions start at 1.0; the build number comes from the CI run.
- **Listing:** name "Street Gym: Daily Calisthenics" (exactly 30 characters, Apple's limit); subtitle (30), keywords (100) and promotional text (170) in the `play-listing` voice; saved in `docs/play/streetgymnastics/app-store.md`.
- **Screenshots:** 6.9-inch, 1320 × 2868, from the simulator tour. Icon 1024 × 1024 with no transparency.
- **Declarations:** App Privacy (ads data through AdMob, nothing else in 1.0), the age rating, `ITSAppUsesNonExemptEncryption = NO`, and `PrivacyInfo.xcprivacy` with the UserDefaults reason `CA92.1`.
- **Release:** internal TestFlight on a real iPhone for one check, then Submit for review. Add an `ios-release` skill beside `update-live-app` once the first upload works.

## GitHub CI: `ios-ci.yml` in the standalone repo

Written on 18 Nov 2026 as `ios-streetgym/ci/ios-ci.yml`, with its first two jobs (`screens` on Linux, `simulator` on macOS 26); it has not run yet. It goes next to `android-ci.yml` in `.github/workflows/` of the standalone repo, with actions pinned by SHA as there. The `testflight` job below comes with the Apple account (phase 5).

```yaml
on:
  push: { branches: [main], tags: ['ios-v*'] }   # ios- tags: v* tags are the Android GitHub releases
  workflow_dispatch:
jobs:
  engine:      # ubuntu: ./gradlew :shared:compileCommonMainKotlinMetadata :app:testDebugUnitTest
  simulator:   # macos-15 or newer, Xcode 26 selected (Apple requires the iOS 26 SDK for uploads)
               # ./gradlew :shared:compileKotlinIosSimulatorArm64 first; cache ~/.konan and Gradle
               # brew install xcodegen; xcodegen in iosApp
               # xcodebuild test -scheme StreetGymTour -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max'
               # xcresulttool export attachments -> upload the screenshots as an artifact
  testflight:  # only on ios-v* tags; needs: simulator; environment: app-store
               # API key from secrets -> xcodebuild archive -allowProvisioningUpdates
               #   -authenticationKeyPath/-authenticationKeyID/-authenticationKeyIssuerID (cloud-managed signing)
               # xcodebuild -exportArchive with ExportOptions destination=upload -> TestFlight
```

- **Secrets** (the owner enters them in GitHub): `ASC_KEY_ID`, `ASC_ISSUER_ID` and `ASC_KEY_P8` (base64), in an `app-store` environment. Forked pull requests never see secrets. Keep the `.p8` off `C:\MyDrive`.
- **Reviewing a run:** download the screenshot artifact with `gh run download` and review it like the emulator shots.

## What the owner does

1. **Apple Developer Program, 99 USD a year, as an Organization** so the seller shows "justdataplease". It needs a free D-U-N-S number, which takes days to two weeks: start this first.
2. **App Store Connect:** the Paid Apps agreement, tax forms and bank details, and the Small Business Program (15% commission).
3. **API key:** an App Store Connect API key with the Admin role, and the three secrets in the repo.
4. **Real iPhone:** one TestFlight install for the real-device check.
5. **Submit for review.**

Claude does the rest, using Claude in Chrome for the web consoles as with Play: the app record, products, App Privacy, age rating, the listing and the AdMob iOS app.

## Apple rules to respect in 1.0

| Rule | What it means here |
|---|---|
| 3.1.1 | Unlocks only through StoreKit; no web purchase links. Restore purchases button. |
| 3.1.2 | Subscription screen shows price, period, terms and privacy links. |
| 2.3.1 | No hidden features: drop the long-press review code on iPhone. Apple tests purchases in the sandbox. |
| 5.1.1 | App Privacy answers and the privacy manifest; the existing privacy page. |
| ATT | Ask before personalised ads; ads still show without it. |
| 1.4.1 | Fitness: keep the Train safely text and the readiness note. |
| 2.1 | No crashes: the real-device TestFlight check before submitting. |

## Risks

- **No Mac for debugging.** An iPhone-only crash is read from CI logs, the xcresult and TestFlight crash reports; rent a cloud Mac for a day if that isn't enough.
- **CI time.** Kotlin/Native builds take 10–20 minutes per run; cache `~/.konan`.
- **Native ads inside Compose lists** (`UIKitView` sizing) is the least certain interop piece. Try it early in phase 4.
- **App size.** CMP adds about 9 MB. Leave `spots.tsv` (5 MB) out until the parks map comes.

## Rough timing

| Phase | Work |
|---|---|
| 1 Shared engine | done, 2.11.5 |
| 2 Shell and simulator CI | built 18 Nov 2026; the first macOS run is left |
| 3 Screens | 2–3 weeks |
| 4 Purchases and ads | about 1 week |
| 5 TestFlight, listing, review | a few days plus Apple's review |

## Open decisions

1. Is 1.0 without account, cloud backups and leaderboard acceptable?
2. Whose iPhone does the TestFlight check?
3. Developer account as an Organization (recommended) or as an individual?
4. Later: should Pro bought on Android unlock on iPhone? This needs server-side entitlements and the account.
5. iPhone only at first (recommended), or iPad too?
