package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.figure.BadgeStyle
import com.justdataplease.streetgym.app.figure.ExerciseFigure
import com.justdataplease.streetgym.app.figure.FigureHold
import com.justdataplease.streetgym.app.figure.LocalFigureHold
import com.justdataplease.streetgym.app.figure.LocalMotionLibrary
import com.justdataplease.streetgym.app.figure.WorkoutTile
import com.justdataplease.streetgym.data.effort.Effort
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.blendColor
import com.justdataplease.streetgym.app.ui.BodyText
import com.justdataplease.streetgym.json.JSONObject
import androidx.compose.ui.platform.LocalDensity
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.ExamQuestion
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.app.ui.Shown
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.progress.ExamResult
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
    // The whole page down to the workout view, Easier / Normal / Harder and Start; and a completed class to repeat.
    shot(File(out, "class-long.png"), height = LONG_HEIGHT) { StreetGymApp(athlete, SAFE_AREA, startAt = Screen.Class(nextId)) }
    val firstId = NextClass.ordered(program.catalog).first().id
    shot(File(out, "class-repeat.png")) { StreetGymApp(athlete, SAFE_AREA, startAt = Screen.Class(firstId)) }
    shot(File(out, "today-new.png")) { StreetGymApp(model(program), SAFE_AREA) }
    for (theme in listOf(StreetTheme.NIGHT_PARK, StreetTheme.WHEATPASTE)) {
        val themed = athlete(program).apply { this.theme = theme }
        shot(File(out, "today-${theme.key}.png")) { StreetGymApp(themed, SAFE_AREA) }
    }
    for (theme in StreetTheme.entries) workoutShots(program, theme, out)
    for (theme in StreetTheme.entries) loopShots(program, theme, out)
    for (theme in StreetTheme.entries) levelShots(program, theme, out)
    shot(File(out, "start-buttons.png"), height = 1560) { StartButtons() }
    val names = movementNames()
    for (theme in StreetTheme.entries) FIGURE_SHEETS.forEachIndexed { index, ids ->
        shot(File(out, "figures-${index + 1}-${theme.key}.png"), width = SHEET_WIDTH, height = SHEET_HEIGHT, density = SHEET_DENSITY) {
            CompositionLocalProvider(LocalMotionLibrary provides program.motions) {
                StreetGymTheme(theme) { FigureSheet(ids, names) }
            }
        }
    }
    println("Screenshots in ${out.absolutePath}")
}

/**
 * The movements on the review sheets: a hang, pull-ups (straight, archer), a lever; a push-up, a dip, a squat, a Cossack;
 * a wall handstand, a one-arm handstand, a plank, a side plank; a jack, a stretch at a post, a push-up on a box and the
 * band muscle-up. Together they use every layout (both views, projected, facing with a side clip and end-on).
 */
private val FIGURE_SHEETS = listOf(
    listOf("dead_hang", "pullup", "archer_pullup", "tuck_front_hold"),
    listOf("pushup", "parallel_dip", "squat", "cossack_squat"),
    listOf("wall_handstand", "one_arm_handstand", "plank", "side_plank"),
    listOf("low_jack", "hamstring_stretch", "incline_pushup", "band_muscleup"),
)

/** The moments of a loop Android's AnimationGallery draws (0, 0.12, 0.28, 0.6), then 0.28 of the next rep. */
private val SHEET_FRAMES = listOf(0f to 0, 0.12f to 0, 0.28f to 0, 0.6f to 0, 0.28f to 1)
private const val SHEET_DENSITY = 2f
private const val SHEET_WIDTH = 4_160
private const val SHEET_HEIGHT = 1_680
/**
 * The class page's guide on an iPhone 17 Pro Max: 440 points less the page's and the card's margins. Android's gallery
 * draws 340 dp, its class page on a 411 dp phone; at 340 the captions "Side" and "Front" just miss Android's room rule
 * here, as the app's bold face is a little wider than Android's system one.
 */
private val GUIDE_WIDTH = 368.dp

/** The movement library's English names, by id, as the gallery labels its rows. */
private fun movementNames(): Map<String, String> = runBlocking {
    val library = JSONObject(Res.readBytes("files/curriculum.json").decodeToString()).getJSONArray("movement_library")
    (0 until library.length()).associate { index ->
        library.getJSONObject(index).let { it.getString("id") to it.getJSONObject("name").optString("en") }
    }
}

/**
 * One review sheet (Android: AnimationGallery): per movement, the class page's guide ([GUIDE_WIDTH] x 168 dp) at the gallery's
 * moments and the next rep, then a skill badge holding still and a workout tile playing, at two moments.
 */
@Composable
private fun FigureSheet(ids: List<String>, names: Map<String, String>) {
    val theme = Street.theme
    val palette = theme.palette
    val badge = BadgeStyle(palette.infoSurface, palette.primary, blendColor(palette.primary, palette.infoSurface, 0.5f),
        blendColor(palette.textSecondary, palette.infoSurface, 0.4f))
    Column(Modifier.fillMaxSize().pageBackground(theme).padding(12.dp)) {
        for (id in ids) {
            val name = names[id] ?: id
            BodyText("$name  [$id]", 14f, Modifier.padding(top = 8.dp, bottom = 2.dp), color = palette.text, bold = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                for ((phase, cycle) in SHEET_FRAMES) CompositionLocalProvider(LocalFigureHold provides FigureHold(phase, cycle)) {
                    ExerciseFigure(id, name, Modifier.size(GUIDE_WIDTH, 168.dp))
                }
                Spacer(Modifier.width(12.dp))
                ExerciseFigure(id, name, Modifier.size(64.dp), badge = badge)
                // A class row's tile (Android: WorkoutTile), ringed as a Moderate workout.
                val exercise = TrainingExercise(id, movementId = id, number = 1, name = LocalizedText(en = name),
                    description = LocalizedText.EMPTY, sets = emptyList())
                for (phase in listOf(0f, 0.28f)) CompositionLocalProvider(LocalFigureHold provides FigureHold(phase)) {
                    Spacer(Modifier.width(10.dp))
                    WorkoutTile(exercise, Effort.MODERATE, 52.dp)
                }
            }
        }
    }
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

    // The list view of the same class: the warm-up and practice ticked, the strength work's first set just ticked in
    // the list and its rest running; then the whole list, top to bottom, in a tall picture.
    val (list, listId) = training { flow ->
        flow.steps.indices.first { flow[it].section == "main" && (flow[it].set.breakSeconds ?: 0) > 0 && flow.next(it) != null }
    }
    list.workoutView = WorkoutView.LIST
    list.training.state()!!.let { state -> list.training.tick(state.flow[(state.page as FocusPage.Set).index].key, true) }
    shot(File(out, "workout-list-$name.png")) { StreetGymApp(list, SAFE_AREA, startAt = Screen.Workout(listId)) }
    shot(File(out, "workout-list-long-$name.png"), height = LONG_HEIGHT) { StreetGymApp(list, SAFE_AREA, startAt = Screen.Workout(listId)) }
}

/**
 * Round 2, the workout loop, in [theme]: an exam class's page before and after an attempt, its question after the class,
 * "Exam passed" and the block offered again, Today repeating the block, a class not reached, a class of a level the tier
 * doesn't open with Unlock and its offer, Today with Unlock, and Settings › During your workout.
 */
private fun loopShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val ordered = NextClass.ordered(program.catalog)
    val exam = ordered.first { it.isExam && it.headline != null }
    val examIndex = ordered.indexOf(exam)
    fun atExam(tier: Tier = Tier.PRO): AppModel = model(program, tier = tier).apply {
        this.theme = theme
        progress.completePrograms(ordered.take(examIndex).map { it.id }, at = NOW - DAY)
    }

    val untaken = atExam()
    shot(File(out, "class-exam-$name.png")) { StreetGymApp(untaken, SAFE_AREA, startAt = Screen.Class(exam.id)) }
    shot(File(out, "class-exam-long-$name.png"), height = LONG_HEIGHT) { StreetGymApp(untaken, SAFE_AREA, startAt = Screen.Class(exam.id)) }

    // The question after the class: every target reached as ticked but the headline, logged short.
    val tests = exam.tests.distinct().map { it to ((it.movementId ?: it.id) != exam.headline) }
    val question = ExamQuestion(exam, tests, offerRepeat = true)
    shot(File(out, "exam-ask-$name.png")) { StreetGymApp(atExam(), SAFE_AREA, showing = Shown.Exam(question)) }
    shot(File(out, "exam-passed-$name.png")) { StreetGymApp(atExam(), SAFE_AREA, showing = Shown.ExamPassed) }
    val missed = tests.filterNot { it.second }.map { it.first }
    shot(File(out, "exam-repeat-$name.png")) { StreetGymApp(atExam(), SAFE_AREA, showing = Shown.RepeatBlock(exam, missed)) }

    // After the attempt: its card on the exam's page, and Today going back through the block.
    val taken = atExam()
    taken.progress.completePrograms(listOf(exam.id), at = NOW - 60 * MINUTE)
    taken.saveExam(question, tests.map { it.second })
    taken.repeatBlock(exam)
    shot(File(out, "class-exam-result-$name.png")) { StreetGymApp(taken, SAFE_AREA, startAt = Screen.Class(exam.id)) }
    shot(File(out, "today-replay-exam-$name.png")) { StreetGymApp(taken, SAFE_AREA) }

    // A class not reached yet, and one of a level Free doesn't open, with Unlock and the offer; Today on Unlock.
    val athlete = athlete(program).apply { this.theme = theme }
    shot(File(out, "class-later-$name.png")) { StreetGymApp(athlete, SAFE_AREA, startAt = Screen.Class(ordered[20].id)) }
    val free = model(program, tier = Tier.FREE).apply {
        this.theme = theme
        progress.completePrograms(ordered.take(300).map { it.id }, at = NOW - DAY)
    }
    shot(File(out, "class-locked-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Class(ordered[300].id)) }
    shot(File(out, "today-unlock-$name.png")) { StreetGymApp(free, SAFE_AREA) }
    shot(File(out, "offer-level-$name.png")) { StreetGymApp(free, SAFE_AREA, showing = Shown.Offer(4)) }

    shot(File(out, "settings-$name.png")) { StreetGymApp(athlete, SAFE_AREA, startAt = Screen.Settings) }
}

/**
 * Round 3, Training › Levels, in [theme]: the levels of an athlete in Level 3 on Free (two levels done and stamped,
 * the current one, those ahead, the tier's tags), a replay's card, Level 1's classes as the current level and as an
 * earlier one with its exams, a locked level's classes, and Today with large text.
 */
private fun levelShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val ordered = NextClass.ordered(program.catalog)
    val veteran = athlete(program, skip = 200, tier = Tier.FREE).apply { this.theme = theme }
    // Level 1's first exam passed, its second not yet.
    val exams = ordered.filter { it.isExam && it.levelNumber == 1 }
    veteran.progress.recordExam(ExamResult(exams[0].id, NOW - 30 * DAY, listOf("pullup"), emptyList()))
    veteran.progress.recordExam(ExamResult(exams[1].id, NOW - 25 * DAY, emptyList(), listOf("pullup")))
    shot(File(out, "training-$name.png")) { StreetGymApp(veteran, SAFE_AREA, startAt = Screen.Training) }
    shot(File(out, "training-long-$name.png"), height = 9_000) { StreetGymApp(veteran, SAFE_AREA, startAt = Screen.Training) }
    shot(File(out, "level-done-$name.png")) { StreetGymApp(veteran, SAFE_AREA, startAt = Screen.Level(1)) }
    shot(File(out, "level-locked-$name.png")) { StreetGymApp(veteran, SAFE_AREA, startAt = Screen.Level(4)) }

    val athlete = athlete(program).apply { this.theme = theme }
    shot(File(out, "level-$name.png")) { StreetGymApp(athlete, SAFE_AREA, startAt = Screen.Level(1)) }
    shot(File(out, "level-long-$name.png"), height = LONG_HEIGHT) { StreetGymApp(athlete, SAFE_AREA, startAt = Screen.Level(1)) }

    val replaying = athlete(program, skip = 105).apply {
        this.theme = theme
        goBackTo(1)
    }
    shot(File(out, "training-replay-$name.png")) { StreetGymApp(replaying, SAFE_AREA, startAt = Screen.Training) }
    shot(File(out, "today-replay-$name.png")) { StreetGymApp(replaying, SAFE_AREA) }

    // 130% text: the streak's smaller flame and boxes.
    shot(File(out, "today-large-text-$name.png")) {
        CompositionLocalProvider(LocalDensity provides Density(DENSITY, fontScale = 1.3f)) { StreetGymApp(athlete, SAFE_AREA) }
    }
}

/** A tall picture for a whole scrolling screen. */
private const val LONG_HEIGHT = 15_000

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

private fun model(program: Program, clock: ShotClock = ShotClock(), tier: Tier = Tier.PRO) =
    AppModel(program, StoredProgress(MemoryStore()), tier = tier, now = { clock.time }, zone = TimeZone.UTC)

/**
 * Twelve classes trained: one a day with a rest day here and there, the last four on Saturday, Sunday, Monday and
 * today. [skip]: the classes before them marked done a month ago, for an athlete further on.
 */
private fun athlete(program: Program, clock: ShotClock = ShotClock(), skip: Int = 0, tier: Tier = Tier.PRO): AppModel =
    model(program, clock, tier).also { model ->
    val daysAgo = listOf(17, 16, 14, 13, 11, 10, 8, 7, 4, 3, 2, 0)
    val ordered = NextClass.ordered(program.catalog)
    if (skip > 0) model.progress.completePrograms(ordered.take(skip).map { it.id }, at = NOW - 30 * DAY)
    ordered.drop(skip).take(daysAgo.size).forEachIndexed { index, day ->
        // Today's class was trained in the morning; the one in the pictures comes after it.
        val finished = NOW - daysAgo[index] * DAY - (if (daysAgo[index] == 0) 8 * 60 * MINUTE else 0L)
        model.progress.startWorkout(day.id, startedAtEpochMillis = finished - 40 * MINUTE)
        model.progress.finishWorkout(completedAtEpochMillis = finished, effortRating = 6, points = 120)
    }
}

/**
 * Draws [content] to [file]. Every movement figure holds still at 0.28 of its loop (the top of a pull-up, the bottom of
 * a push-up, where Android's gallery takes its main frame), so the pictures are the same on every run.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun shot(file: File, width: Int = WIDTH, height: Int = HEIGHT, density: Float = DENSITY, content: @Composable () -> Unit) {
    val scene = ImageComposeScene(width, height, Density(density)) {
        CompositionLocalProvider(LocalFigureHold provides (LocalFigureHold.current ?: FigureHold(0.28f))) { content() }
    }
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
