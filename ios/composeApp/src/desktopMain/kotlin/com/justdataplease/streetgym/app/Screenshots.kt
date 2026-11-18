package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.ic_play_arrow_white_24dp
import com.justdataplease.streetgym.app.resources.resume_workout
import com.justdataplease.streetgym.app.resources.start_workout
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.ui.PrimaryButton
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.pageBackground
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.ui.WorkoutFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** An iPhone 17 Pro Max: 440 x 956 points at 3x, with its status bar and home indicator kept clear. */
private const val WIDTH = 1320
private const val HEIGHT = 2868
private const val DENSITY = 3f
private val SAFE_AREA = PaddingValues(top = 62.dp, bottom = 34.dp)

private const val DAY = 86_400_000L
private const val MINUTE = 60_000L
/** Wednesday 18 November 2026, 18:00 UTC: a fixed "now", so the pictures are the same on every run. */
private const val NOW = 1_795_024_800_000L

/** The time in the pictures; a class is trained up to [NOW]. */
private class ShotClock(var time: Long = NOW)

/**
 * Writes a PNG of every screen into the folder given as the first argument: `gradlew :composeApp:screenshots`.
 * The athlete in the pictures has trained twelve classes, four of them this week, and is training the thirteenth.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/screenshots").apply { mkdirs() }
    val program = runBlocking { loadProgram() }
    val athlete = athlete(program)
    val nextId = athlete.today().next!!.id
    shot(File(out, "today.png")) { StreetGymApp(athlete, SAFE_AREA) }
    shot(File(out, "class.png")) { StreetGymApp(athlete, SAFE_AREA, startAt = Screen.Class(nextId)) }
    shot(File(out, "today-new.png")) { StreetGymApp(model(program), SAFE_AREA) }
    for (theme in listOf(StreetTheme.NIGHT_PARK, StreetTheme.WHEATPASTE)) {
        val themed = athlete(program).apply { this.theme = theme }
        shot(File(out, "today-${theme.key}.png")) { StreetGymApp(themed, SAFE_AREA) }
    }
    for (theme in StreetTheme.entries) workoutShots(program, theme, out)
    shot(File(out, "start-buttons.png"), height = 1560) { StartButtons() }
    println("Screenshots in ${out.absolutePath}")
}

/**
 * The focus workout of today's class in [theme]: a set of reps in the strength work, a timed set counting down, the
 * rest after a set, the class's last set, the summary, and the effort question at the end.
 */
private fun workoutShots(program: Program, theme: StreetTheme, out: File) {
    fun training(until: (WorkoutFlow) -> Int?): Pair<AppModel, String> {
        val clock = ShotClock()
        val model = athlete(program, clock).apply { this.theme = theme }
        val id = model.today().next!!.id
        clock.time = NOW - 25 * MINUTE
        model.start(id)
        val flow = model.training.state()!!.flow
        val stop = until(flow) ?: flow.size
        // A set every 50 seconds, up to the set the picture shows; the last tick is now.
        val ticks = (model.training.state()!!.page as? FocusPage.Set)?.let { stop - it.index } ?: 0
        clock.time = NOW - ticks * 50_000L
        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            if (index >= stop) break
            clock.time += 50_000L
            model.training.done(index)
            model.training.skipRest()
        }
        clock.time = NOW
        return model to id
    }
    val name = theme.key

    val (set, setId) = training { flow ->
        flow.steps.indices.firstOrNull { flow[it].section == "main" && flow[it].set.repetitions != null && flow[it].exercise.easier != null }
            ?: flow.steps.indices.first { flow[it].section == "main" }
    }
    shot(File(out, "workout-set-$name.png")) { StreetGymApp(set, SAFE_AREA, startAt = Screen.Workout(setId)) }

    val (hold, holdId) = training { flow -> flow.steps.indices.first { flow[it].set.repetitions == null && (flow[it].set.durationSeconds ?: 0) > 0 } }
    (hold.training.state()!!.page as FocusPage.Set).let { page ->
        hold.training.startHold(page.index, 1, hold.training.state()!!.flow[page.index].set.durationSeconds!!)
    }
    shot(File(out, "workout-hold-$name.png")) { StreetGymApp(hold, SAFE_AREA, startAt = Screen.Workout(holdId)) }

    // The rest: the strength work's first set with a rest after it was just ticked.
    val (rest, restId) = training { flow ->
        flow.steps.indices.first { flow[it].section == "main" && (flow[it].set.breakSeconds ?: 0) > 0 && flow.next(it) != null }
    }
    rest.training.state()!!.let { state -> rest.training.done((state.page as FocusPage.Set).index) }
    shot(File(out, "workout-rest-$name.png")) { StreetGymApp(rest, SAFE_AREA, startAt = Screen.Workout(restId)) }

    val (last, lastId) = training { flow -> flow.size - 1 }
    shot(File(out, "workout-last-$name.png")) { StreetGymApp(last, SAFE_AREA, startAt = Screen.Workout(lastId)) }

    // The summary, with the last set skipped: "End of class", the skipped set, and Do skipped sets beside Back.
    val (summary, summaryId) = training { flow -> flow.size - 1 }
    (summary.training.state()!!.page as FocusPage.Set).let { summary.training.skip(it.index) }
    shot(File(out, "workout-summary-$name.png")) { StreetGymApp(summary, SAFE_AREA, startAt = Screen.Workout(summaryId)) }

    val (effort, effortId) = training { null }
    shot(File(out, "workout-effort-$name.png")) { StreetGymApp(effort, SAFE_AREA, startAt = Screen.Workout(effortId, askEffort = true)) }
}

/** The class page's Start workout and Resume in each theme, with Android's play arrow. */
@Composable
private fun StartButtons() {
    Column(Modifier.fillMaxSize()) {
        for (theme in StreetTheme.entries) StreetGymTheme(theme) {
            Column(Modifier.weight(1f).pageBackground(theme).padding(20.dp)) {
                PrimaryButton(stringResource(Res.string.start_workout), icon = painterResource(Res.drawable.ic_play_arrow_white_24dp)) {}
                PrimaryButton(stringResource(Res.string.resume_workout), Modifier.padding(top = 8.dp),
                    icon = painterResource(Res.drawable.ic_play_arrow_white_24dp)) {}
            }
        }
    }
}

private fun model(program: Program, clock: ShotClock = ShotClock()) =
    AppModel(program, StoredProgress(MemoryStore()), now = { clock.time }, zone = TimeZone.UTC)

/** Twelve classes trained: one a day with a rest day here and there, the last four on Saturday, Sunday, Monday and today. */
private fun athlete(program: Program, clock: ShotClock = ShotClock()): AppModel = model(program, clock).also { model ->
    val daysAgo = listOf(17, 16, 14, 13, 11, 10, 8, 7, 4, 3, 2, 0)
    NextClass.ordered(program.catalog).take(daysAgo.size).forEachIndexed { index, day ->
        // Today's class was trained in the morning; the one in the pictures comes after it.
        val finished = NOW - daysAgo[index] * DAY - (if (daysAgo[index] == 0) 8 * 60 * MINUTE else 0L)
        model.progress.startWorkout(day.id, startedAtEpochMillis = finished - 40 * MINUTE)
        model.progress.finishWorkout(completedAtEpochMillis = finished, effortRating = 6, points = 120)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun shot(file: File, width: Int = WIDTH, height: Int = HEIGHT, content: @Composable () -> Unit) {
    val scene = ImageComposeScene(width, height, Density(DENSITY)) { content() }
    try {
        // A few frames, so fonts, pictures and dialogs that come after the first one are in.
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
