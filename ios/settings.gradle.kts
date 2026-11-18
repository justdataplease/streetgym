pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "StreetGymIos"
include(":composeApp", ":shared")

// The engine both phones run on. The suite keeps it in android-streetgym/shared; in the standalone repository this
// project is the ios/ folder and the engine is shared/ beside it.
project(":shared").projectDir = listOf("../android-streetgym/shared", "../shared").map { file(it) }.first { it.isDirectory }
