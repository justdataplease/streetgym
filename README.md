# StreetGym

StreetGym is a free, open-source Android app for street workout and calisthenics training. It walks you through one guided sequence of 600 workouts, from your first rows and incline push-ups to muscle-ups, levers and handstands, and adds a separate Street Basketball track for court days. Everything works offline. There are no ads, no analytics and no tracking.

## Features

- **600 guided calisthenics workouts** in **6 levels of 100**: Foundations, Bar Strength, Skill Transition, Skill Control, Intermediate Strength and Confident Calisthenics.
- **A warm-up and stretching for every workout.** Each session starts with movement-specific preparation and ends with flexibility work and a cooldown, with easy recovery mornings scheduled in every level.
- **124 offline animated movements.** Every exercise has a small animated figure with pause, slow motion and variation previews. They are drawn on the device from JSON keyframes, so no video is needed.
- **Street Basketball tab** with **48 court sessions**, **45 drills**, offline **court diagrams**, repeatable sessions and saved skill checks. Basketball progress is tracked separately from calisthenics.
- **One clear next step.** The Today screen assigns the next workout. Future sessions can be previewed but not started early.
- **Coaching built in.** Every skill has a listed regression, effort targets (RPE), rest timers with an optional sound cue, and safety notes.
- **Progress and history** stored locally on the device.
- **Optional sign-in.** Email/password accounts via Firebase Authentication when the build is configured for it. Training never requires an account.

## Building

Requirements:

- Android SDK with platform 35 (Android Studio installs it)
- JDK 17 (Android Studio's bundled `jbr` works)
- Internet access on the first build, so Gradle can download dependencies

```bash
./gradlew assembleDebug          # macOS / Linux
gradlew.bat assembleDebug        # Windows
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Application ID: `com.justdataplease.streetgym` (debug builds add `.debug`). Minimum Android version: 6.0 (API 23). The project uses Android Gradle Plugin 8.7.3, Kotlin 2.1.0, Gradle 8.10.2 and compile/target SDK 35.

### Instrumented tests

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.justdataplease.streetgym.debug.test/com.justdataplease.streetgym.BasketballInstrumentation
```

### Optional sign-in (Firebase)

The app builds and runs without Firebase. If no Firebase configuration is bundled, the Account section is hidden from Settings and everything else works the same.

To enable email/password sign-in:

1. Create a Firebase project and enable **Authentication → Sign-in method → Email/Password**.
2. Register Android apps for `com.justdataplease.streetgym` and `com.justdataplease.streetgym.debug`.
3. Create `app/src/release/res/values/firebase.xml` and `app/src/debug/res/values/firebase.xml` with each app's public client values:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="google_app_id" translatable="false">1:000000000000:android:0000000000000000</string>
    <string name="google_api_key" translatable="false">YOUR_API_KEY</string>
    <string name="project_id" translatable="false">your-project-id</string>
    <string name="gcm_defaultSenderId" translatable="false">000000000000</string>
</resources>
```

These files are git-ignored, so each fork uses its own Firebase project. Never add a service-account or Admin SDK key to the app or the repository.

## Training content

The training plan is plain JSON that you can read, review and change:

- `curriculum/*.json`: the authored sessions, movement library, morning-class timetable and level workload ceilings.
- `generated/curriculum_v2.json` and `app/src/main/assets/curriculum.json`: the assembled 600-workout catalog the app reads.
- `app/src/main/assets/exercise_animations.json`: poses and templates for the animated movement guides. `tools/animation_authoring/` regenerates it, and `tools/preview_animations.py` renders contact sheets for review.
- `app/src/main/assets/basketball_curriculum.json`: the Street Basketball track. See [docs/BASKETBALL_PROGRAM.md](docs/BASKETBALL_PROGRAM.md).

The scripts assemble and check the catalog. They never invent workouts:

```powershell
powershell -ExecutionPolicy Bypass -File tools\assemble_curriculum.ps1
powershell -ExecutionPolicy Bypass -File tools\validate_curriculum.ps1
powershell -ExecutionPolicy Bypass -File tools\validate_animations.ps1
python tools\validate_morning_class.py
powershell -ExecutionPolicy Bypass -File tools\validate_basketball.ps1
```

`tools\package_dist.ps1` runs all of the above, builds and lints the app, and writes a checksummed bundle to the git-ignored `dist/` folder. The validators need Windows PowerShell 5.1+ and Python 3 with Pillow.

The programming rationale, research sources and safety boundaries are in [docs/CURRICULUM_METHOD.md](docs/CURRICULUM_METHOD.md) and [docs/COACHING_UX_REVIEW.md](docs/COACHING_UX_REVIEW.md).

### Brand assets

The launcher icon (an athlete on a pull-up bar) and the in-app wordmark are generated from one geometry definition:

```bash
python tools/brand/generate_brand_assets.py --play-icon streetgym-icon-512.png
```

This writes the adaptive icon (foreground, background and monochrome layers), the legacy mipmap PNGs and `drawable/logo.xml`.

## Safety

StreetGym provides general exercise programming, not medical advice. Use sound equipment and spotting where needed. Use the listed regression whenever you cannot control a movement. Stop if you feel sharp pain, numbness, dizziness or instability. If you are returning from injury or managing a medical condition, get individual advice first.

## License

StreetGym is released under the [MIT License](LICENSE). Copyright (c) 2026 JustDataPlease.
