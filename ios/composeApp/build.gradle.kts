plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val composeVersion = "1.12.2"

// The Android app, whose texts, fonts, icons and program this app shows unchanged: android-streetgym/app in the suite,
// app/ at the root of the standalone repository (where this project is ios/).
val androidMain: File = listOf("../../android-streetgym/app", "../../app").map { file(it) }
    .first { it.isDirectory }.resolve("src/main")

// One source for both phones: the Android app's files are copied in at build time, never kept twice in git.
val androidResources = tasks.register<Sync>("androidResources") {
    into(layout.buildDirectory.dir("androidResources"))
    // Gradle can't see a change to the rewriting rules below; raise this when one changes, so the copy runs again.
    inputs.property("rules", 7)
    // The parks (about 5 MB, OpenStreetMap's), read when Training › Parks first opens on Plus and Pro.
    from(androidMain.resolve("assets")) {
        include("curriculum.json", "workouts.json", "exercise_animations.json", "spots.tsv")
        into("files")
    }
    from(androidMain.resolve("assets/fonts")) { into("font") }
    from(androidMain.resolve("res/values")) {
        include("strings*.xml")
        into("values")
        // Compose fills only numbered places (%1$d), and Android's texts with a single number write a bare %d.
        // Android also writes an apostrophe as \' and a quote as \" in its string files; Compose would show the backslash.
        // Its bold <b>…</b> is kept as written (escaped here, so Compose reads it as text), for ui/Kit.kt boldMarkup.
        // Compose fills no decimals either: "%1$.1f km away" takes the number already written with one (ui/DisplayText.kt
        // oneDecimal), and Android's "%%" is its percent sign.
        filter { line: String ->
            line.replace("%d", "%1${'$'}d").replace("%1${'$'}.1f", "%1${'$'}s").replace("%%", "%")
                .replace("\\'", "'").replace("\\\"", "\"")
                .replace("<b>", "&lt;b&gt;").replace("</b>", "&lt;/b&gt;")
        }
    }
    from(androidMain.resolve("res/drawable")) {
        include("ic_nav_*.xml", "ic_brand_mark.xml", "ic_lock.xml", "ic_view_list.xml", "ic_view_focus.xml",
            "ic_heart.xml", "ic_heart_outline.xml", "logo.xml", "ic_my_location.xml")
        into("drawable")
        // Compose reads Android's vector format, but not references to Android's own colours.
        filter { line: String -> line.replace("@android:color/transparent", "#00000000") }
    }
    // Start workout's play arrow: a picture only, at Android's xxhdpi, which is the iPhone's 3x; the screens size it.
    from(androidMain.resolve("res/drawable-xxhdpi")) {
        include("ic_play_arrow_white_24dp.png")
        into("drawable")
    }
    // What only this app has.
    from("src/commonMain/composeResources")
    // The app's contact address, for the leaderboard's report mail: `contact.address` in this project's
    // local.properties (never in git: the repository is public), else STREETGYM_CONTACT_ADDRESS, else empty.
    val contact = contactAddress()
    inputs.property("contact", contact)
    doLast {
        val values = destinationDir.resolve("values").apply { mkdirs() }
        values.resolve("strings_build.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <string name=\"contact_address\">$contact</string>\n</resources>\n")
    }
}

fun contactAddress(): String {
    val local = rootProject.file("local.properties")
    // Read line by line: in a build script the name `java` is Gradle's Java extension, not the package.
    val fromFile = if (local.isFile) local.readLines().map { it.trim() }
        .firstOrNull { it.startsWith("contact.address=") }?.substringAfter("=") else null
    return (fromFile ?: System.getenv("STREETGYM_CONTACT_ADDRESS") ?: "").trim()
}

kotlin {
    // Windows, macOS and Linux: runs the same screens in a window and writes the review screenshots.
    jvm("desktop")

    // The iPhone and the Simulator on Apple-chip Macs; these compile only on macOS.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "StreetGymApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            // The movement figures' frame clock (withInfiniteAnimationFrameNanos), already under foundation.
            implementation("org.jetbrains.compose.animation:animation-core:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        named("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        named("desktopTest") {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
                // Drives the screens by taps in tests (runComposeUiTest).
                implementation("org.jetbrains.compose.ui:ui-test:$composeVersion")
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.justdataplease.streetgym.app.resources"
    customDirectory("commonMain", layout.dir(androidResources.map { it.destinationDir }))
}

compose.desktop {
    application {
        mainClass = "com.justdataplease.streetgym.app.PreviewKt"
    }
}

// The screens' tests draw whole screens and read the program in each class: Gradle's default 512 MB worker ran out
// once rounds 11 to 13's tests came, and its test worker then hung instead of ending.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
}

// Renders each screen to a PNG without a window, for review: `gradlew :composeApp:screenshots` writes
// composeApp/build/screenshots (or `-Pshots=<folder>`).
tasks.register<JavaExec>("screenshots") {
    group = "verification"
    description = "Writes a PNG of every screen at iPhone size."
    val desktop = kotlin.targets.getByName("desktop").compilations.getByName("main") as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
    classpath = files(desktop.output.allOutputs, desktop.runtimeDependencyFiles)
    mainClass.set("com.justdataplease.streetgym.app.ScreenshotsKt")
    val out = providers.gradleProperty("shots").orElse(layout.buildDirectory.dir("screenshots").map { it.asFile.path })
    // `-Ponly=timer+parks` draws only those rounds' pictures (the names in Screenshots.kt's main), for a quick look.
    args(listOf(out.get()) + providers.gradleProperty("only").orNull.let { listOfNotNull(it) })
    systemProperty("java.awt.headless", "true")
}
