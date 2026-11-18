package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadCatalog
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.data.model.CurriculumCatalog
import com.justdataplease.streetgym.data.progress.NextClass
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** An iPhone 17 Pro Max: 440 x 956 points at 3x, with its status bar and home indicator kept clear. */
private const val WIDTH = 1320
private const val HEIGHT = 2868
private const val DENSITY = 3f
private val SAFE_AREA = PaddingValues(top = 62.dp, bottom = 34.dp)

private const val DAY = 86_400_000L
/** Wednesday 18 November 2026, 18:00 UTC: a fixed "now", so the pictures are the same on every run. */
private const val NOW = 1_795_024_800_000L

/**
 * Writes a PNG of every screen into the folder given as the first argument: `gradlew :composeApp:screenshots`.
 * The athlete in the pictures has trained twelve classes, four of them this week.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/screenshots").apply { mkdirs() }
    val catalog = runBlocking { loadCatalog() }
    val athlete = athlete(catalog)
    val nextId = athlete.today().next!!.id
    shot(File(out, "today.png")) { StreetGymApp(athlete, SAFE_AREA) }
    shot(File(out, "class.png")) { StreetGymApp(athlete, SAFE_AREA, startAt = Screen.Class(nextId)) }
    shot(File(out, "today-new.png")) { StreetGymApp(model(catalog), SAFE_AREA) }
    for (theme in listOf(StreetTheme.NIGHT_PARK, StreetTheme.WHEATPASTE)) {
        val themed = athlete(catalog).apply { this.theme = theme }
        shot(File(out, "today-${theme.key}.png")) { StreetGymApp(themed, SAFE_AREA) }
    }
    println("Screenshots in ${out.absolutePath}")
}

private fun model(catalog: CurriculumCatalog) =
    AppModel(catalog, StoredProgress(MemoryStore()), now = { NOW }, zone = TimeZone.UTC)

/** Twelve classes trained: one a day with a rest day here and there, the last four on Saturday, Sunday, Monday and today. */
private fun athlete(catalog: CurriculumCatalog): AppModel = model(catalog).also { model ->
    val daysAgo = listOf(17, 16, 14, 13, 11, 10, 8, 7, 4, 3, 2, 0)
    NextClass.ordered(catalog).take(daysAgo.size).forEachIndexed { index, program ->
        val finished = NOW - daysAgo[index] * DAY
        model.progress.startWorkout(program.id, startedAtEpochMillis = finished - 40 * 60_000L)
        model.progress.finishWorkout(completedAtEpochMillis = finished, effortRating = 6, points = 120)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun shot(file: File, content: @Composable () -> Unit) {
    val scene = ImageComposeScene(WIDTH, HEIGHT, Density(DENSITY)) { content() }
    try {
        // A few frames, so fonts and pictures that load after the first one are in.
        var image = scene.render(0)
        repeat(FRAMES) { frame ->
            Thread.sleep(FRAME_MILLIS)
            image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000)
        }
        file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    } finally {
        scene.close()
    }
}

private const val FRAMES = 6
private const val FRAME_MILLIS = 50L
