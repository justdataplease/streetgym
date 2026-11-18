# Street Gym for iPhone

The iPhone app, started on 18 Nov 2026. The Android app is `android-streetgym/`; both run on the same engine (`android-streetgym/shared/`) and the same program, texts, fonts and icons, which this project reads from the Android app at build time.

**Where it stands:** the loading screen, the strength test for a new athlete, Today with the park scene, the class page (with Repeat this day for a completed class, and the heart that saves a day), training a class, exams, Training › Levels with each level's classes, Training › My workouts (saved days, lists, own workouts with their builder), Progress (Skills with its medals and ranks, History's calendar and the streak's sheet) and Settings (the themes, During your workout, the skill and equipment switches, Start over, Train safely, About) are built, in the three themes, with progress and choices saved under Android's names. The switches act where Android's do, in the class lists, both workout views, exams and Skills, and a swipe from the left edge goes back. A class trains in Focus (set by set, with the rest timer, its beep and spoken cues, the effort question and finishing) or as a List of every set with its tick, switched from the class page, Settings or the workout's title bar. A rest that ends with the app in the background has a notification with the same beep. Every exercise shows the moving line man, side and front view, ported from Android. They build, pass their tests and are drawn to pictures on Windows; GitHub's Mac builds the app and runs it in the iPhone Simulator (`ci/ios-ci.yml`). The plan: [docs/iphone-development.md](docs/iphone-development.md); every Android feature and whether it is on iPhone: [docs/checklist.md](docs/checklist.md).

| Folder | What it holds |
|---|---|
| `composeApp/` | The screens, in Compose Multiplatform: iPhone targets, and a desktop target for Windows |
| `iosApp/` | The Xcode project as `project.yml` (XcodeGen), the Swift entry point, the icon, the Simulator tour |
| `ci/ios-ci.yml` | The GitHub checks; it goes in the standalone repo as `.github/workflows/ios-ci.yml` |
| `docs/` | The plan |

## On Windows

JDK 21, with `ANDROID_HOME` set (the engine also has an Android target). From the suite, through the Gradle gate:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File C:\MyDrive\jdp\android-apps\tools\gradle-gate.ps1 `
  -Root C:\MyDrive\jdp\android-apps\ios-streetgym -Tasks :composeApp:compileCommonMainKotlinMetadata,:composeApp:desktopTest,:composeApp:screenshots
```

- `:composeApp:run` opens the screens in a window at iPhone size.
- `:composeApp:screenshots` writes a PNG of every screen and state to `composeApp/build/screenshots` (an iPhone 17 Pro Max, 1320 × 2868), most in each theme (`*-concrete`, `*-night_park`, `*-wheatpaste`), tall pictures of whole scrolling screens (`*-long*.png`), and the line man's review sheets (`figures-<n>-<theme>.png`, as Android's AnimationGallery draws them).
- `:composeApp:compileCommonMainKotlinMetadata` proves the shared code uses no Java or Android API. The desktop build alone lets Java calls through.
- `:composeApp:compileIosMainKotlinMetadata` type-checks the iPhone-only Kotlin (`iosMain`) against Apple's APIs, on Windows.

## On a Mac (CI)

```
cd iosApp && xcodegen generate
xcodebuild test -project StreetGym.xcodeproj -scheme StreetGym -destination 'platform=iOS Simulator,name=<an iPhone>' CODE_SIGNING_ALLOWED=NO
```

The Xcode build runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` itself.

## The standalone repository

In `justdataplease/streetgym` this project is the `ios/` folder, beside `app/` and `shared/`. Its build files find the Android app and the engine in either layout.
