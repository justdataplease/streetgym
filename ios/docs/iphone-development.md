# Street Gym on iPhone: development plan

Written 3 Nov 2026, updated 18 Nov 2026.

- **Phase 1 is done** (2.11.5): the engine lives in the shared Kotlin Multiplatform module `android-streetgym/shared/`, and the Android app runs on it.
- **Phase 2 has started** (18 Nov 2026): this folder, `ios-streetgym/`, is the iPhone project. Today, the class page, training a class (Focus or List, the rest timer with its sounds, finishing, exams, repeating a completed class or a block), Training › Levels with each level's classes, and Settings › During your workout are built on the engine, in the three themes, with the moving line man on every exercise. They build, pass their tests and are drawn to pictures on Windows; GitHub's Mac builds them and runs them in the iPhone Simulator. What is on iPhone, line by line: [checklist.md](checklist.md) (122 of 344 features).

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
| `ui/ExerciseAnimationView.kt`, `ui/FrontView.kt` | The line man. Ported to the iPhone on 18 Nov 2026 (`composeApp`, `figure/`) with the maths split from the drawing: `Motion.kt` (poses, rig, curve, clips, finding an exercise's clip) and `FrontView.kt` (the front view and the projection) are plain Kotlin, line by line Android's. **Engine step:** move those two files into `shared/` and have Android's view draw from them, so one copy reads `exercise_animations.json` for both phones |
| `data/progress/SharedPreferencesProgressRepository.kt`, `data/library/LibraryRepository.kt`, `data/CurriculumRepository.kt` | Storage and asset reading; the iPhone app writes its own (NSUserDefaults, the bundle) behind the same interfaces. Its progress store (`composeApp`, `data/StoredProgress.kt`) writes sessions, the class in progress and exams with the same JSON names as Android, pinned by a test. **Next engine step:** move that codec into `shared/` and have Android use it too, so one piece of code writes both; it needs an Android release with the stored data checked both ways |
| `ui/HomeActivity.kt` (requestStart, askEffortAndFinish, switchEasier, measureOf, skillMovement, sections, renderActive's ticks), `ui/FocusWorkoutView.kt` (position, skips, timed sets) | The rules of training a class, mixed with views. The iPhone has them once more in `composeApp`, `data/Training.kt`, calling the engine for every count. **Engine step:** move them into `shared/` (a class's four sections and their keys, what finishing saves, the focus position, the timed set's rounds, a list tick and a section tick with the rest they start or end), so both phones run one copy |
| `ui/AppSettings.kt` (workoutView, keepScreenAwake, soundCues, intensityToday, skillSwitches, equipment) | The iPhone keeps them under the same names and values (`data/AppSettings.kt`, `streetgym_settings.*`; switches as "true"/"false", the pick's day as its number) |
| `ui/HomeActivity.kt` (levelReplay, passedIds, furthestProgram, askExam's result, askRepeatBlock's block, workoutEffort, renderDashboard's label), `ui/EffortUi.kt` (tileExercise), `leaderboard/Leaderboard.kt` (Scores.credits), `data/progress/StreakDays.kt` (isoWeekday) | Rules mixed with views or Android types. The iPhone has them once more in `composeApp`, `data/AppModel.kt`, with the same names. **Engine step:** move them into `shared/` beside NextClass and DayAssignment, so Today, Training and the widget run one copy on both phones |
| `data/progress/SharedPreferencesProgressRepository.kt` (the level replay, putRepeated) | The iPhone keeps the replay under Android's keys (`replay_level` as its number in text, `replay_repeated_ids` as a JSON array, `replay_from_program_id`), and counts the days repeated as Android does, Complete day on a class done before included |
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
- **The line man** (18 Nov 2026), ported from Android's `ExerciseAnimationView` and `FrontView` into `composeApp`, `figure/`: the maths in plain Kotlin (`Motion.kt`, `FrontView.kt`), the drawing on Compose's `Canvas` with the same paints (`FigureDraw.kt`, `FigureRenderer.kt`), the playing view in `ExerciseFigure.kt`. Same rig, curve, tempo rep, start frame, colours per theme, stroke widths, apparatus (bars, dip bars, boxes, walls, posts), the side view with the front view beside it (its own front motion, the clip projected, or a facing clip with its side clip or end-on), the range line, the phase label, the tempo strip, the captions and the mirrored next rep. It plays while on screen and stands still with Reduce Motion. It is on the focus workout's panel, on every exercise of the class page (with the variation button Android has for exercises known by name), and in the list. Android's Details sheet has no figure, so neither has the iPhone's. The skill badge and the class row's tile (`WorkoutTile`) are drawn too, ready for the Training tab.
- **The list view** (18 Nov 2026, Android's `renderActive`): the class, its time, the sets ticked and the rest line; the safety line; each section with a tick for all its sets; each exercise with its line man, notes, "Needs:", easier option and one ticked row per set ("Did more or fewer?" on credited sets, asked at once on a "max" set); Finish workout and End session. A tick starts the set's rest, which Focus shows too. Focus · List is picked on the class page and switched in the workout's title bar (the list or focus icon), and kept on the phone. The list keeps the tab bar, as on Android. **End session** is in the list only, as on Android; the focus summary lost the stopgap button.
- **Repeating a completed class**: its page offers Repeat this day at the top and the end, with its note, the workout view and Easier / Normal / Harder; Today resumes a repeat in progress with the repeat note and no Complete day. A repeat adds a history entry and leaves the program's place as it was.
- Complete day asks first ("Complete this day?") on Today, the class page and the list, and the safety line ends in "Safety notes ›", which opens the class's readiness and safety notes, as on Android. A class page also shows what each exercise needs ("Needs: pull-up bar").
- **The workout loop** (round 2, 18 Nov 2026): the class page's "Moderate for you" badge (the engine's WorkoutEffort with the athlete's best sets), a class not reached ("This daily plan is locked."), a done class a tier no longer opens, and an exam's card ("Last taken Nov 18, 2026: 4 of 5 targets reached. Not yet: …"). After an exam class: "Exam: tick the targets you reached" (targets of switched-off skills or equipment left out), then "Exam passed", "Not every target yet" with Repeat N classes (a level replay from the block's first class), or the note that they are tested again; a repeated exam offers no block. The replay is kept under Android's names and ends by itself at the furthest class. Settings › During your workout: Keep screen awake (the list only; Focus always keeps the screen on, as on Android), Rest timer sound, the workout view. The sounds and the spoken cues are below.
- **Sounds and cues on iPhone** (`iosMain`, `MainViewController.kt`, behind `WorkoutDevice`): Android's beep (ToneGenerator's TONE_PROP_BEEP, 400 + 1200 Hz for 35 ms) is written as a WAV by common code (`data/Beep.kt`) into Library/Sounds once and played as a system sound, which mixes with the athlete's music and keeps quiet on silent; a timed set's end also vibrates. A rest that ends while the app is in the background has a local notification with the same beep, "Rest over. Set 2 of 3, Pull-Up"; it is replaced by +15 s and taken away by Skip rest, the rest's end on screen, or leaving the workout. Alerts are asked for where Android asks for its notification permission: only the Timer's Enable alerts (round 8; `WorkoutDevice.askAlerts`), so until then the notification shows only where alerts are already allowed. A rest or timed set seen ending more than 2 s late doesn't beep again. The focus view speaks Android's cues to VoiceOver (rest started with what's next, every 15 s of rest, rest over, time's up, switch sides); the list doesn't, as on Android. Apple APIs: `UIApplication.idleTimerDisabled`; AudioToolbox `AudioServicesCreateSystemSoundID`, `AudioServicesPlaySystemSound`, `kSystemSoundID_Vibrate`; UserNotifications `UNUserNotificationCenter` (`requestAuthorizationWithOptions`, `addNotificationRequest`, `removePendingNotificationRequestsWithIdentifiers`, `removeDeliveredNotificationsWithIdentifiers`), `UNMutableNotificationContent`, `UNNotificationSound.soundNamed`, `UNTimeIntervalNotificationTrigger`, `UNNotificationRequest`; `UIAccessibilityPostNotification` with `UIAccessibilityAnnouncementNotification`; Foundation `NSSearchPathForDirectoriesInDomains`, `NSFileManager`, `NSData`, `NSURL`, `CFBridgingRetain`/`Release`, `NSCalendar.currentCalendar.firstWeekday`, `NSNotificationCenter` with `UIApplicationDidBecomeActiveNotification`. `project.yml` links AudioToolbox and UserNotifications.
- **Today and Training › Levels** (round 3, 18 Nov 2026): Training's four tabs (Levels; My workouts, Library and Parks still to come), the intro, a replay's card with Return to my furthest day, and the ten levels, dressed as Android dresses them (Concrete's tape-yellow current level with its hard shadow and dashed levels ahead, Night Park's route with its stations, Wheatpaste's tilted posters, the current one blue, finished ones stamped DONE). A level's page: its description and progress, the tier's tag on a locked level, the Level 1 hint and Complete level on the current level, Go back to this level on an earlier one, and a row per class (the moving tile of its hardest exercise ringed in its effort colour, number, name, meta with "Easy for you" once reached, Exam · passed / not yet, ✓, NEXT, LATER, SWITCHED OFF, Plus or Pro). A level, a class and a workout light the Training tab, as on Android. On Today: the words over the title during a repeat or a replay, Unlock with the tier's sticker on a class of a locked level (the class page shows the same), the day's Easier / Normal / Harder kept on the phone until the next local day, the flame redrawn after midnight (on each return to the app and every 30 s on Today), the week row starting on the region's first weekday, and the smaller streak with large text. Until purchases exist (round 10), Unlock and the tier tags open the offer's heading and line in Android's words ("Level 4 is part of Plus") with Cancel.
- 79 tests on the desktop: the progress store, the settings and the beep (common), the Android texts, Today and Complete day, Harder, and training over the real program (start, ticks with their times, the rest stepped by the clock, timed sets, finishing with the engine's points and streak, too-fast ticks earning nothing, End session, resuming after the store is read again, skips, the easier option, the countdown and points texts); the line man (joints of known poses against Android's own formulas, Android's FrontViewTest and AnimationCuesTest run on the port, every movement of the program, the ready workouts, the easier options and the library drawn in the three themes); the list (a tick and its rest, a section tick, Focus and List in step, finishing, Complete day and End session from the list, repeating a class, the class page's view switch); exams (the targets and their result as Android stores it, the block repeated as a level replay under Android's names until the exam is taken again, a repeated exam, switched-off targets, the whole flow by taps); Training › Levels and a level's classes by taps (states, marks, Complete level, going back and returning, locked levels on Free with Unlock and the offer); the day's pick across restarts and midnight, the flame redrawn with a stepped clock, the region's first weekday, the smaller streak at 130% text; Settings › During your workout under Android's keys, the screen kept on per view, the beeps and vibration with the sound on and off, the rest's notification, and the focus view's spoken cues, through a recording stand-in for the phone.
- `iosApp` hosts it: the Swift entry point, the XcodeGen project, the privacy manifest, the 1024 px icon, and a Simulator tour that saves a picture of Today, of the workout's first set, of the list view, of Training and of Level 1's classes (only the first step asserts).

Still to do in this phase:

- ~~The first macOS run.~~ Done 18 Nov 2026: `ios/` is in the standalone repo and `ios-ci.yml` runs on every push there. `shared/` and `composeApp` compile for `iosSimulatorArm64`, the common tests run on the Simulator, XcodeGen and Xcode build the app, and the tour opens Today and starts the workout on an iPhone 17 Pro Max. Both runs so far were green without a code change. The pictures come back as the run's `simulator-…` files.
- Not in the app yet: the park scene on the brand card, the strength test for a new athlete, the streak's sheet, Progress and the Timer, Training's other three tabs, and the rest of Settings (after a class Android opens Progress; the iPhone returns to Today until Progress exists). The checklist's build order says what comes when.

From here on, most checks happen on Windows and CI confirms on iPhone.

## Phase 3: screens

**Rebuild in CMP**, from about 14,000 lines of Android Views:
- Home and Today (done in phase 2)
- Training (Levels and a level's classes are done; My workouts, Library and Parks to come), workout overview (the class page is done)
- Focus workout, list view, rest timer with its sounds, effort dialog, exams, the line man, repeating a class or a block, Training › Levels (done on 18 Nov 2026, see phase 2); still to come for training:
  - skill and equipment switches set in Settings (sets left out of a workout, greyed exercises, "Do them anyway"); the sequence, the class rows and the exams already follow the switches' saved values
  - the ad breaks between sections, the class page's heart
  - Reduce Motion is read when the app starts; a change while it runs shows after a restart
  - the line man's words ("Side", "Hold · breathe", "Chin over bar") are in the app's bold face; Android draws them in the system's. Albert Sans is a little wider, so at a few class-page widths around Android's 340 dp the side and front captions are left out where Android just fits them (they show at the iPhone 17 Pro Max's 368 dp and in the focus panel)
- Skills medals (rank shield and Plus/Pro as tags on the corners, segmented bars), skill switches
- History calendar and the month's list
- Ready workouts and filters, Timer tab
- Placement, Settings and themes, the plans panel, the park scene (it draws with the line man, now ported: `drawLineFigure` and the clips are in `figure/`)

**Drawing.** `Canvas`/`Paint`/`Path` code maps closely to Compose's `Canvas` (under every `DrawScope`): the line man keeps Android's mutable paints and calls (`figure/FigureDraw.kt`). `:composeApp:screenshots` draws its review sheets as Android's AnimationGallery does (the gallery's moments 0, 0.12, 0.28 and 0.6, the next rep, a badge and a tile) for sixteen movements in the three themes; compare them with the gallery or emulator shots (`artifacts/play/streetgymnastics/captures/`: `focus_set.png`, `class_moves.png`).

**Themes.** Concrete & Tape and the other two free themes become CMP colour tokens. Drawables and decorations (`ThemeDecor`, `ThemeDrawables`) are redrawn in `DrawScope`.

**Strings.** Done in phase 2: a Gradle task copies `app/src/main/res/values/strings*.xml` into `composeApp`'s resources at build time, so the texts have one source (`AppOnDesktopTest` checks an apostrophe, a bare `%d`, numbered places, `&amp;` and a plural). Still to handle when their screens come: `%1$.1f` in one string, `%%` in one, and the `<b>` in `share_body`.

**iPhone details:** the screen is kept awake during a workout (`idleTimerDisabled`, done); the rest's and the timed set's beep as a system sound in the foreground and a scheduled local notification in the background (done, phase 2); respect safe areas; support the back swipe (to do).

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
| 2 Shell and simulator CI | done 18 Nov 2026; green on GitHub's Mac |
| 3 Screens | 2–3 weeks |
| 4 Purchases and ads | about 1 week |
| 5 TestFlight, listing, review | a few days plus Apple's review |

## Open decisions

1. Is 1.0 without account, cloud backups and leaderboard acceptable?
2. Whose iPhone does the TestFlight check?
3. Developer account as an Organization (recommended) or as an individual?
4. Later: should Pro bought on Android unlock on iPhone? This needs server-side entitlements and the account.
5. iPhone only at first (recommended), or iPad too?
