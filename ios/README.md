# Street Gym for iPhone

The iPhone app, started on 18 Nov 2026. The Android app is `android-streetgym/`; both run on the same engine (`android-streetgym/shared/`) and the same program, texts, fonts and icons, which this project reads from the Android app at build time.

**Where it stands:** Today and the class page are built, in the three themes, with progress saved. They build, pass their tests and are drawn to pictures on Windows. Nothing has been compiled for iPhone yet; that needs a macOS runner (`ci/ios-ci.yml`, not run so far). The plan, what is done and what is next: [docs/iphone-development.md](docs/iphone-development.md).

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
- `:composeApp:screenshots` writes a PNG of every screen to `composeApp/build/screenshots` (an iPhone 17 Pro Max, 1320 × 2868).
- `:composeApp:compileCommonMainKotlinMetadata` proves the shared code uses no Java or Android API. The desktop build alone lets Java calls through.

## On a Mac (CI)

```
cd iosApp && xcodegen generate
xcodebuild test -project StreetGym.xcodeproj -scheme StreetGym -destination 'platform=iOS Simulator,name=<an iPhone>' CODE_SIGNING_ALLOWED=NO
```

The Xcode build runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` itself.

## The standalone repository

In `justdataplease/streetgym` this project is the `ios/` folder, beside `app/` and `shared/`. Its build files find the Android app and the engine in either layout.
