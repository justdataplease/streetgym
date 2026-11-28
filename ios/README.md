# Street Gym for iPhone

The iPhone app, started on 18 Nov 2026. The Android app is `android-streetgym/`; both run on the same engine (`android-streetgym/shared/`) and the same program, texts, fonts and icons, which this project reads from the Android app at build time.

**Where it stands:** the loading screen, the strength test for a new athlete, Today with the park scene, the class page (with Repeat this day for a completed class, and the heart that saves a day), training a class, exams, Training › Levels with each level's classes, Training › My workouts (saved days, lists, own workouts with their builder), Training › Library (the ready workouts with their filters), Training › Parks (the parks near the athlete on a MapLibre map or as a list, the search, directions in Google Maps), Progress (Skills with its medals and ranks, History's calendar and the streak's sheet, Free's leaderboard under its layer), the Timer tab (timer, stopwatch and alarm, with their alerts), Settings (the themes, Plus and Pro, During your workout, the skill and equipment switches, Start over, Train safely, About with Ad privacy), Plus and Pro bought through StoreKit 2 with every lock's offer, Free's ads (Google's native cards and full-screen ad, consent first), the "Next class" home-screen widget, Settings' Profile with Google and Sign in with Apple and Pro's cloud copy, the leaderboard with report and block, progress analytics, and Google Analytics in the app (Android 2.15.0's screen views and events, collected only with Share my progress on and the consent of Google's form) are built, in the three themes, with progress and choices saved under Android's names. After the phone closes the app in the background it opens again where it was. The switches act where Android's do, in the class lists, both workout views, exams and Skills, and a swipe from the left edge goes back. A class trains in Focus (set by set, with the rest timer, its beep and spoken cues, the effort question and finishing) or as a List of every set with its tick, switched from the class page, Settings or the workout's title bar. A rest that ends with the app in the background has a notification with the same beep. Every exercise shows the moving line man, side and front view, ported from Android. Since the parity round of 19 Nov 2026 it looks and behaves as Android does wherever iOS allows it: Roboto where Android draws the system face, the alarm's clock face, Android's version in About, Google Maps for directions, the beeps Android plays on silent, Reduce Motion followed as it changes, and Android's landscape layouts (the app turns); what still differs is listed at the end of the checklist. The widget waits for the owner's App Group, and the account, the leaderboard and both analytics for the owner's Firebase iPhone app (`GoogleService-Info.plist`, never in git: a build without it shows Android's "isn't available in this build"). They build, pass their tests and are drawn to pictures on Windows; GitHub's Mac builds the app and runs it in the iPhone Simulator (`ci/ios-ci.yml`). On 28 Nov 2026 it was also built and toured on the owner's Mac (Xcode 27.2, iOS 27.2 Simulator, iPhone 18 Pro Max): every Swift file compiles and the whole tour passes. The plan: [docs/iphone-development.md](docs/iphone-development.md); every Android feature and whether it is on iPhone: [docs/checklist.md](docs/checklist.md).

| Folder | What it holds |
|---|---|
| `composeApp/` | The screens, in Compose Multiplatform: iPhone targets, and a desktop target for Windows |
| `iosApp/` | The Xcode project as `project.yml` (XcodeGen, with MapLibre, Google Mobile Ads, Google's consent, Firebase and Google Sign-In by Swift Package Manager), the Swift entry point, what must be Swift (the Parks map, StoreKit 2, Google's ads, Firebase, the widget's files), the `StreetGymWidget` extension (SwiftUI), the StoreKit test file `Products.storekit`, the icon, the Simulator tour |
| `ci/ios-ci.yml` | The GitHub checks; it goes in the standalone repo as `.github/workflows/ios-ci.yml` |
| `docs/` | The plan |

## On Windows

JDK 21, with `ANDROID_HOME` set (the engine also has an Android target). From the suite, through the Gradle gate:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File C:\MyDrive\jdp\android-apps\tools\gradle-gate.ps1 `
  -Root C:\MyDrive\jdp\android-apps\ios-streetgym -Tasks :composeApp:compileCommonMainKotlinMetadata,:composeApp:desktopTest,:composeApp:screenshots
```

- `:composeApp:run` opens the screens in a window at iPhone size.
- `:composeApp:screenshots` writes a PNG of every screen and state to `composeApp/build/screenshots` (an iPhone 17 Pro Max, 1320 × 2868), most in each theme (`*-concrete`, `*-night_park`, `*-wheatpaste`), tall pictures of whole scrolling screens (`*-long*.png`), and the line man's review sheets (`figures-<n>-<theme>.png`, as Android's AnimationGallery draws them). `-Ponly=ready+timer+parks+workout+plans+ads+widget+account+parity` draws only those rounds (`parity`: the screens sideways, `*-landscape-*.png`, and a 375-point class page). On the desktop purchases go to a fake store at Android's prices, ads are labelled stand-in cards (`desktopMain`, `Fakes.kt`), and Firebase is a fake that checks every write against firestore.rules (`AccountFakes.kt`); the widget is drawn from the same snapshot as on iPhone (`widget-*.png`). On Windows the Parks map is a stand-in that draws the parks as dots; the real one (MapLibre, in Swift) is seen on the Mac.
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
