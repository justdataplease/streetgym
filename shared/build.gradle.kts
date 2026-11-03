plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// Street Gym's engine, shared with the iPhone app to come (docs/iphone-development.md): the curriculum and its
// readers, progress, placement, effort, points, skills and ranks, and the tiers. Plain Kotlin only, so the same code
// runs on both phones and day ids, points and ranks always match.
kotlin {
    androidLibrary {
        namespace = "com.justdataplease.streetgym.shared"
        compileSdk = 36
        minSdk = 24
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    // The iPhone targets compile only on a Mac (GitHub's macOS runners); Windows and Linux builds skip them.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
    }
}
