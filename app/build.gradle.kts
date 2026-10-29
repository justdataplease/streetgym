plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Toolbox account by default; per-app overrides support separate AdMob registrations.
// Debug never reads live IDs. Standalone CI without account settings uses demo IDs.
fun nativeAdId(kind: String, release: Boolean): String {
    if (!release && kind == "nativeId" && providers.gradleProperty("admob.testNativeVideo").orNull == "true")
        return "ca-app-pub-3940256099942544/1044960115"
    val demo = if (kind == "appId") "ca-app-pub-3940256099942544~3347511713" else "ca-app-pub-3940256099942544/2247696110"
    return if (release) providers.gradleProperty("admob.streetgymnastics.$kind")
        .orElse(providers.gradleProperty("admob.$kind")).getOrElse(demo) else demo
}

// Optional separate native placement ID for the immediately dismissible full-screen format.
fun fullScreenNativeId(release: Boolean): String = if (release)
    providers.gradleProperty("admob.streetgymnastics.fullscreenNativeId")
        .orElse(providers.gradleProperty("admob.fullscreenNativeId")).getOrElse(nativeAdId("nativeId", true))
    else nativeAdId("nativeId", false)

android {
    namespace = "com.justdataplease.streetgym"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.justdataplease.streetgym"
        minSdk = 23
        targetSdk = 35
        versionCode = 19
        versionName = "2.5.0"
        testInstrumentationRunner = "com.justdataplease.streetgym.TrainingInstrumentation"

        vectorDrawables.useSupportLibrary = false
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = nativeAdId("appId", false)
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"${nativeAdId("nativeId", false)}\"")
            buildConfigField("String", "ADMOB_FULLSCREEN_NATIVE_ID", "\"${fullScreenNativeId(false)}\"")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // The GitHub APK carries the map's native library for every phone ABI plus the x86_64 emulator; the
            // 32-bit x86 copy (12 MB) serves no phone. Play builds are bundles, split per ABI by Play.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
        release {
            manifestPlaceholders["admobAppId"] = nativeAdId("appId", true)
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"${nativeAdId("nativeId", true)}\"")
            buildConfigField("String", "ADMOB_FULLSCREEN_NATIVE_ID", "\"${fullScreenNativeId(true)}\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
        )
    }

    bundle {
        language {
            enableSplit = false
        }
    }

}

dependencies {
    implementation("com.android.billingclient:billing:8.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-auth")
    // Pro: cloud progress and the leaderboard. App Check keeps the public leaderboard reads to the genuine app.
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    // Sign in with Google through Credential Manager (Pro only).
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Parks map: MapLibre with OpenFreeMap tiles, the same as JustMaps (no API key, no per-view cost).
    implementation("org.maplibre.gl:android-sdk:11.11.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
