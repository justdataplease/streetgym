plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.justdataplease.streetgym"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.justdataplease.streetgym"
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "1.3.0"
        testInstrumentationRunner = "com.justdataplease.streetgym.BasketballInstrumentation"

        vectorDrawables.useSupportLibrary = false
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = false
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
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-auth")
}
