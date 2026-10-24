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

android {
    namespace = "com.justdataplease.streetgym"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.justdataplease.streetgym"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "1.4.0"
        testInstrumentationRunner = "com.justdataplease.streetgym.TrainingInstrumentation"

        vectorDrawables.useSupportLibrary = false
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = nativeAdId("appId", false)
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"${nativeAdId("nativeId", false)}\"")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            manifestPlaceholders["admobAppId"] = nativeAdId("appId", true)
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"${nativeAdId("nativeId", true)}\"")
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
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-auth")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
