plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    // The engine (:shared) also builds for Android; this project never builds that target.
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
}

// The engine's folder belongs to the Android app, whose own Gradle projects build it too. What this project builds of
// it stays in this project's build folder: two Gradle roots writing one build folder leave each other broken outputs.
project(":shared") {
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("engine"))
}
