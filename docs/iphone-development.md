# Street Gym on iPhone: development plan

Written 3 Nov 2026. **Phase 1 is done** (2.11.5): the engine lives in the shared Kotlin Multiplatform module `shared/`, the Android app runs on it, and its iPhone targets are declared. No iPhone code exists yet. For background on every suite app, see the suite's `IOS-PORTING-REPORT.md`.

## Approach

- **No Mac.** Code is written on the Windows PC. GitHub's macOS runners compile the app, run it in the iPhone Simulator and return screenshots. Signed builds also go to TestFlight from there.
- **CI runs in the public `justdataplease/streetgym` repo**, where macOS minutes are free. The private suite repo would be billed for them.
- **One engine.** Curriculum, progress, placement, points, skills and ranks, effort and the tiers are in `shared/`. Android and iPhone both use it and load the same `curriculum.json`, `workouts.json` and `exercise_animations.json`, so day ids, points and ranks always match.
- **Screens in Compose Multiplatform (CMP).** The iPhone screens are new Kotlin code. Their module gets a desktop target, so screens can be run and screenshotted on Windows without a CI round trip.
- **Android keeps its Views.** Moving Android to the CMP screens is a separate decision for later.
- **Swift only where Apple requires it:** StoreKit 2, Google ads, and the app entry point.

## The engine today (`shared/`)

Module `:shared` in the standalone repo, `:streetgymnastics-shared` in the suite. Plugins: `org.jetbrains.kotlin.multiplatform` and `com.android.kotlin.multiplatform.library` (AGP 9.4.1, Kotlin 2.3.21). The targets are Android, `iosArm64` and `iosSimulatorArm64`. The iPhone targets compile only on a Mac; `kotlin.native.ignoreDisabledTargets=true` keeps Windows and Linux builds quiet about them.

**Everything is in `commonMain`, in the same packages as before**, so the Android app's imports didn't change:

| Package | Files | What it does |
|---|---|---|
| `data.model` | `Curriculum.kt` | The catalog: levels, classes, exercises, sets, `LocalizedText` |
| `data` | `CurriculumJsonParser.kt` | Reads `curriculum.json` and `workouts.json` into the catalog |
| `data` | `Clock.kt` | `epochMillisNow()`, for the default times |
| `data.skills` | `SkillBook.kt`, `SkillCurriculumReader.kt`, `SkillStandards.kt`, `SkillSwitches.kt` | The 40 skills, ranks 1–5, targets and credits, skill switches |
| `data.progress` | `ProgressRepository.kt` | The progress interface, `WorkoutSession` and `ActiveWorkout` |
| `data.progress` | `Placement.kt`, `NextClass.kt`, `LevelReplay.kt`, `Intensity.kt`, `ExamLog.kt`, `ProgressMerge.kt` | The strength test, next class, repeats, easier/harder, exams, cloud merges |
| `data.points` | `Points.kt` | Points per set and rank, all-time and month totals (UTC months without a platform calendar) |
| `data.effort` | `WorkoutEffort.kt` | Easy/Moderate/Hard "for you" |
| `data.library` | `WorkoutLibrary.kt`, `ExerciseLibrary.kt` | Saved days, custom days, lists, ready workouts and their JSON codec |
| `billing` | `Tier.kt`, `LevelAccess.kt` | Free / Plus / Pro and which levels each opens |
| `json` | `Json.kt` | The part of org.json the engine uses (same names and lenient reading), on kotlinx.serialization |

**Rules for shared code:**
- `commonMain` uses no Java or Android API. `./gradlew :streetgymnastics-shared:compileCommonMainKotlinMetadata` (suite) or `:shared:compileCommonMainKotlinMetadata` (standalone) checks it on Windows. The Android compile alone would let `java.*` through.
- JSON goes through `com.justdataplease.streetgym.json`, never `org.json`. `SharedEngineTest` pins it to org.json both ways, so data stored before 2.11.5 reads back unchanged.
- Times are epoch milliseconds; UTC dates come from `UtcDate` in `Points.kt`.
- The 282 unit tests stay in the Android app (`app/src/test`) and test the shared classes from there. `DayIdsTest` must pass untouched.

**Still Android-only, to port or rewrite with the iPhone app:**

| Where | Why it stayed |
|---|---|
| `data/progress/HistoryCalendar.kt` | The month grid uses `java.util.Calendar` in the phone's time zone; port to kotlinx-datetime |
| `timer/TimingState.kt` | Same, for the timer's day boundaries |
| `ui/ParkStory.kt` | Pure logic for the park scene, but uses `TimeZone`; move with the park scene |
| `ui/ExerciseAnimationView.kt`, `ui/FrontView.kt` | The line man: pose reading and projection maths are mixed with Canvas drawing; split them, move the maths, redraw in Compose `DrawScope` |
| `data/progress/SharedPreferencesProgressRepository.kt`, `data/library/LibraryRepository.kt`, `data/CurriculumRepository.kt` | Storage and asset reading; the iPhone app writes its own (NSUserDefaults, the bundle) behind the same interfaces and codecs |
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
| History (calendar and the month's classes) | Parks map (MapLibre iOS) |
| Ready workouts with filters, Timer tab | Progress analytics upload |
| Themes, Settings, safety text | Android Pro unlocking on iPhone |
| Free / Plus / Pro through StoreKit | |
| AdMob native and full-screen ads, consent, ATT | |

**Why the account waits:**
- Offering Google sign-in obliges Apple's sign-in beside it (guideline 4.8).
- Public usernames need report, block and filtering (guideline 1.2).

**Pro on iPhone 1.0** is levels 7–10 and ready workouts. Its texts must not promise cloud backups until those exist.

## Project layout

Everything sits under `streetgymnastics/`, so the existing `-p2` sync carries it to the standalone repo:

| Folder | What it holds |
|---|---|
| `shared/` | The engine (done). |
| `composeApp/` | To come: the CMP screens, with iOS framework targets and a desktop target for Windows previews; assets, fonts and strings as Compose resources. |
| `iosApp/` | To come: `project.yml` for XcodeGen (no hand-edited `.pbxproj`), a Swift entry point hosting the Compose view controller, the StoreKit and ads bridges, `Info.plist`, `PrivacyInfo.xcprivacy` and the icon. |
| `app/` | The Android app, on `shared/`. |

Ignore `DerivedData`, `xcuserdata`, `*.xcuserstate` and the generated `.xcodeproj`.

## Phase 2: iPhone shell and simulator CI

- First CI step: compile `shared/` for `iosSimulatorArm64` on a macOS runner. It has never been compiled for iOS, so this is where any remaining JVM-only call shows up.
- `composeApp` shows one real screen (Today) from the shared engine, and `iosApp` hosts it.
- The `ios-ci.yml` workflow (below) builds for the simulator and runs a UI-test tour that saves screenshots. No Apple account is needed for any of this.
- The desktop target renders the same screen on Windows. From here on, most checks happen on Windows and CI confirms on iOS.

## Phase 3: screens

**Rebuild in CMP**, from about 14,000 lines of Android Views:
- Home and Today
- Training (levels, workout lists), workout overview
- Focus workout, rest timer, effort dialog
- Skills medals (rank shield and Plus/Pro as tags on the corners, segmented bars), skill switches
- History calendar and the month's list
- Ready workouts and filters, Timer tab
- Placement, Settings and themes, the plans panel, the park scene and the animations

**Drawing.** `Canvas`/`Paint`/`Path` code maps closely to Compose `DrawScope`. Port it as directly as possible so the line man looks identical. Compare desktop renders with Android emulator shots for a fixed set of exercises.

**Themes.** Concrete & Tape and the other two free themes become CMP colour tokens. Drawables and decorations (`ThemeDecor`, `ThemeDrawables`) are redrawn in `DrawScope`.

**Strings.** A Gradle task copies `app/src/main/res/values/strings*.xml` into `composeApp`'s resources at build time, so the texts have one source. Check the `\'` escapes, plurals and `%1$d` formats there.

**iPhone details:** keep the screen awake with `isIdleTimerDisabled`; play the rest-end sound with AVAudio in the foreground and a scheduled local notification in the background; respect safe areas; support the back swipe.

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

It lives next to `android-ci.yml` in `.github/workflows/` of the standalone repo, with actions pinned by SHA as there.

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
| 2 Shell and simulator CI | 2 days |
| 3 Screens | 2–3 weeks |
| 4 Purchases and ads | about 1 week |
| 5 TestFlight, listing, review | a few days plus Apple's review |

## Open decisions

1. Is 1.0 without account, cloud backups and leaderboard acceptable?
2. Whose iPhone does the TestFlight check?
3. Developer account as an Organization (recommended) or as an individual?
4. Later: should Pro bought on Android unlock on iPhone? This needs server-side entitlements and the account.
5. iPhone only at first (recommended), or iPad too?
