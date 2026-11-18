package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.justdataplease.streetgym.app.figure.LocalParkTime
import com.justdataplease.streetgym.app.figure.ParkRoutine
import com.justdataplease.streetgym.app.figure.ParkScene
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
import com.justdataplease.streetgym.app.ui.PROGRESS_HISTORY
import com.justdataplease.streetgym.app.ui.PROGRESS_LEADERBOARD
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentSwitches
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillProgressions
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.data.skills.switchedOff
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.progress.ExamResult
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.ui.ListPickerDialog
import com.justdataplease.streetgym.data.library.CustomExercise
import androidx.compose.runtime.remember
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
import com.justdataplease.streetgym.app.data.Alerts
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.Products
import com.justdataplease.streetgym.app.data.PhoneLocation
import com.justdataplease.streetgym.app.data.ReadyFilter
import com.justdataplease.streetgym.app.data.Timing
import com.justdataplease.streetgym.app.data.TimingStore
import com.justdataplease.streetgym.app.data.WallClock
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.ui.TRAINING_LIBRARY
import com.justdataplease.streetgym.app.ui.TRAINING_PARKS
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
    // `-Ponly=ready+timer+parks`: only those rounds' pictures.
    val only = args.getOrNull(1)?.split(',', '+')?.map(String::trim)?.toSet()
    if (only != null) {
        for (theme in StreetTheme.entries) {
            if ("ready" in only) readyShots(program, theme, out)
            if ("timer" in only) timerShots(program, theme, out)
            if ("parks" in only) parksShots(program, theme, out)
            if ("workout" in only) workoutShots(program, theme, out)
            if ("plans" in only) plansShots(program, theme, out)
            if ("ads" in only) adsShots(program, theme, out)
            if ("widget" in only) widgetShots(program, theme, out)
            if ("account" in only) accountShots(program, theme, out)
        }
        println("Screenshots in ${out.absolutePath}")
        return
    }
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
    for (theme in StreetTheme.entries) progressShots(program, theme, out)
    for (theme in StreetTheme.entries) settingsShots(program, theme, out)
    for (theme in StreetTheme.entries) placementShots(program, theme, out)
    for (theme in StreetTheme.entries) libraryShots(program, theme, out)
    for (theme in StreetTheme.entries) readyShots(program, theme, out)
    for (theme in StreetTheme.entries) timerShots(program, theme, out)
    for (theme in StreetTheme.entries) parksShots(program, theme, out)
    for (theme in StreetTheme.entries) plansShots(program, theme, out)
    for (theme in StreetTheme.entries) adsShots(program, theme, out)
    for (theme in StreetTheme.entries) widgetShots(program, theme, out)
    for (theme in StreetTheme.entries) accountShots(program, theme, out)
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
    // The largest text sizes: every control stays on screen, the figure and the notes give way first.
    for (scale in listOf(1.6f, 2.2f)) shot(File(out, "workout-set-text-${(scale * 100).toInt()}-$name.png")) {
        CompositionLocalProvider(LocalDensity provides Density(DENSITY, fontScale = scale)) {
            StreetGymApp(set, SAFE_AREA, startAt = Screen.Workout(setId))
        }
    }

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
    shot(File(out, "offer-level-$name.png")) { StreetGymApp(free, SAFE_AREA, showing = Shown.Offer(com.justdataplease.streetgym.app.data.Paywall.Level(4))) }

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

/**
 * Round 4, Progress, in [theme]: Skills with ranks from Rank 1 to Rank 5 and the tags of a lower tier, a skill's sheet,
 * the help; History with its month, a picked day, the month before, a new athlete's empty month; the streak's sheet
 * over Today.
 */
private fun progressShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val ranked = ranked(program).apply { this.theme = theme }
    shot(File(out, "skills-$name.png")) { StreetGymApp(ranked, SAFE_AREA, startAt = Screen.Progress) }
    shot(File(out, "skills-long-$name.png"), height = 9_000) { StreetGymApp(ranked, SAFE_AREA, startAt = Screen.Progress) }
    shot(File(out, "skill-sheet-$name.png")) { StreetGymApp(ranked, SAFE_AREA, startAt = Screen.Progress, showing = Shown.Skill("archer_pullup")) }
    shot(File(out, "skill-sheet-elite-$name.png")) { StreetGymApp(ranked, SAFE_AREA, startAt = Screen.Progress, showing = Shown.Skill("dead_hang")) }
    shot(File(out, "skills-help-$name.png")) { StreetGymApp(ranked, SAFE_AREA, startAt = Screen.Progress, showing = Shown.SkillsHelp) }
    // Free: the skills of the levels it doesn't open wear Plus or Pro; switched-off ones are greyed.
    val free = ranked(program, Tier.FREE).apply {
        this.theme = theme
        skillSwitches = SkillSwitches().with("planche", 2)
    }
    shot(File(out, "skills-free-long-$name.png"), height = 9_000) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Progress) }

    val history = historyAthlete(program).apply { this.theme = theme }
    shot(File(out, "history-$name.png")) { StreetGymApp(history, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_HISTORY) }
    shot(File(out, "history-long-$name.png"), height = 6_000) {
        StreetGymApp(history, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_HISTORY)
    }
    shot(File(out, "history-day-$name.png")) {
        StreetGymApp(history, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_HISTORY, historyDay = 20_261_118)
    }
    shot(File(out, "history-day-marked-$name.png")) {
        StreetGymApp(history, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_HISTORY, historyDay = 20_261_104)
    }
    shot(File(out, "history-october-$name.png")) {
        StreetGymApp(history, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_HISTORY, historyDay = 20_261_004)
    }
    val new = model(program).apply { this.theme = theme }
    shot(File(out, "history-empty-$name.png")) { StreetGymApp(new, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_HISTORY) }
    shot(File(out, "streak-sheet-$name.png")) { StreetGymApp(history, SAFE_AREA, showing = Shown.Streak(history = true)) }
    shot(File(out, "streak-sheet-history-$name.png")) {
        StreetGymApp(history, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_HISTORY, showing = Shown.Streak(history = false))
    }
    shot(File(out, "progress-leaderboard-$name.png")) {
        StreetGymApp(history, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD)
    }
}

/**
 * Round 5, Settings and themes, in [theme]: the whole of Settings with a skill switched off and from a rung and the
 * wall switched off, a skill's steps, Start over's question, the developer settings, a class with a switched-off
 * exercise, the list and the focus summary leaving it out, a class with nothing left, and a class opened to inspect.
 */
private fun settingsShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val ordered = NextClass.ordered(program.catalog)
    val handstand = SkillSwitches().with("handstand", 0)
    val settings = athlete(program).apply {
        this.theme = theme
        skillSwitches = handstand.with("planche", 3).with("pullup", 2)
        equipment = EquipmentSwitches(setOf(Equipment.WALL))
    }
    shot(File(out, "settings-long-$name.png"), height = 10_500) { StreetGymApp(settings, SAFE_AREA, startAt = Screen.Settings) }
    shot(File(out, "switches-sheet-$name.png")) { StreetGymApp(settings, SAFE_AREA, startAt = Screen.Settings, showing = Shown.Switches("planche")) }
    shot(File(out, "switches-sheet-pullup-$name.png")) { StreetGymApp(settings, SAFE_AREA, startAt = Screen.Settings, showing = Shown.Switches("pullup")) }
    shot(File(out, "reset-$name.png")) { StreetGymApp(settings, SAFE_AREA, startAt = Screen.Settings, showing = Shown.Reset) }
    shot(File(out, "developer-$name.png")) { StreetGymApp(settings, SAFE_AREA, startAt = Screen.Settings, showing = Shown.Developer) }

    // A class with handstand work beside other work: greyed on its page, left out of the list's count and the focus view.
    val partial = ordered.first { p ->
        p.levelNumber <= 3 && !p.switchedOff(handstand) && (p.practice + p.exercises).any { it.movementId?.let(handstand::isOff) == true }
    }
    fun atPartial(): AppModel = model(program).apply {
        this.theme = theme
        skillSwitches = handstand
        progress.completePrograms(ordered.takeWhile { it.id != partial.id }.map { it.id }, at = NOW - DAY)
    }
    val page = atPartial()
    shot(File(out, "class-switched-off-long-$name.png"), height = LONG_HEIGHT) { StreetGymApp(page, SAFE_AREA, startAt = Screen.Class(partial.id)) }
    val list = atPartial().apply {
        workoutView = WorkoutView.LIST
        start(partial.id)
    }
    shot(File(out, "workout-list-off-long-$name.png"), height = LONG_HEIGHT) { StreetGymApp(list, SAFE_AREA, startAt = Screen.Workout(partial.id)) }
    val clock = ShotClock(NOW - 30 * MINUTE)
    val focus = model(program, clock).apply {
        this.theme = theme
        skillSwitches = handstand
        progress.completePrograms(ordered.takeWhile { it.id != partial.id }.map { it.id }, at = NOW - DAY)
        start(partial.id)
        while (true) {
            val index = (training.state()!!.page as? FocusPage.Set)?.index ?: break
            clock.time += 40_000L
            training.done(index)
            training.skipRest()
        }
        clock.time = NOW
    }
    shot(File(out, "workout-summary-off-$name.png")) { StreetGymApp(focus, SAFE_AREA, startAt = Screen.Workout(partial.id)) }

    // A class with nothing left to train, opened to inspect it, and a class not reached opened the same way.
    val all = program.skills!!.families.map { it.spec }.fold(SkillSwitches()) { switches, spec ->
        if (SkillProgressions.LINES[spec.id].isNullOrEmpty()) switches else switches.with(spec.id, SkillProgressions.firstSwitchable(spec))
    }
    val passed = ordered.drop(1).first { it.switchedOff(all) }
    val inspector = athlete(program).apply {
        this.theme = theme
        skillSwitches = all
        inspectAllDays = true
    }
    shot(File(out, "class-passed-$name.png")) { StreetGymApp(inspector, SAFE_AREA, startAt = Screen.Class(passed.id)) }
    inspector.skillSwitches = SkillSwitches()
    shot(File(out, "class-inspect-long-$name.png"), height = LONG_HEIGHT) { StreetGymApp(inspector, SAFE_AREA, startAt = Screen.Class(ordered[60].id)) }
}

/**
 * Round 6, the strength test and the park, in [theme]: a new athlete's test, its counts typed, the start it gives with
 * the skills step open and a test picked, a start held to Free; the park on Today's brand card at moments of the day's
 * routine and on other days; the loading screen's two figures and the load error.
 */
private fun placementShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    fun newcomer(tier: Tier = Tier.PRO, counts: List<Int>? = null, skill: Int = 0, shown: Boolean = false) = model(program, tier = tier).apply {
        this.theme = theme
        counts?.forEachIndexed { index, count -> placementForm.set(index, count) }
        placementForm.skill = skill
        placementForm.shown = shown
    }
    shot(File(out, "placement-$name.png")) { StreetGymApp(newcomer(), SAFE_AREA) }
    shot(File(out, "placement-long-$name.png"), height = 6_000) { StreetGymApp(newcomer(), SAFE_AREA) }
    shot(File(out, "placement-filled-$name.png")) { StreetGymApp(newcomer(counts = listOf(10, 30, 0)), SAFE_AREA) }
    shot(File(out, "placement-result-long-$name.png"), height = 7_000) {
        StreetGymApp(newcomer(counts = listOf(10, 30, 0), shown = true), SAFE_AREA)
    }
    shot(File(out, "placement-skills-long-$name.png"), height = 9_000) {
        StreetGymApp(newcomer(counts = listOf(30, 60, 80), skill = 6, shown = true), SAFE_AREA)
    }
    shot(File(out, "placement-free-long-$name.png"), height = 9_000) {
        StreetGymApp(newcomer(Tier.FREE, counts = listOf(30, 60, 80), skill = 6, shown = true), SAFE_AREA)
    }

    // The park: today's routine (squats, a muscle-up, a front lever) at five moments, then six other days at a moment
    // of their own and at their final hold.
    val athlete = athlete(program).apply { this.theme = theme }
    shot(File(out, "park-today-$name.png"), height = 5 * PARK_CARD) {
        CompositionLocalProvider(LocalMotionLibrary provides program.motions) {
            StreetGymTheme(theme) { ParkSheet(listOf(20_775L to 2.2f, 20_775L to 5.5f, 20_775L to 9.4f, 20_775L to 12.6f, 20_775L to null)) }
        }
    }
    shot(File(out, "park-days-$name.png"), height = 12 * PARK_CARD) {
        CompositionLocalProvider(LocalMotionLibrary provides program.motions) {
            StreetGymTheme(theme) {
                ParkSheet(listOf(20_768L, 20_777L, 20_781L, 20_785L, 20_786L, 20_788L).flatMap { listOf(it to 7.5f, it to null) })
            }
        }
    }
    shot(File(out, "today-park-$name.png")) {
        CompositionLocalProvider(LocalParkTime provides 6.5f) { StreetGymApp(athlete, SAFE_AREA) }
    }
    shot(File(out, "loading-walk-$name.png")) { CompositionLocalProvider(LocalMotionLibrary provides program.motions) { StreetGymTheme(theme) { LoadingScreen(pull = false) } } }
    shot(File(out, "loading-pull-$name.png")) { CompositionLocalProvider(LocalMotionLibrary provides program.motions) { StreetGymTheme(theme) { LoadingScreen(pull = true) } } }
    shot(File(out, "load-error-$name.png")) { StreetGymTheme(theme) { LoadErrorScreen(SAFE_AREA) {} } }
}

/** A brand card of the park sheets with its margin, in pixels. */
private const val PARK_CARD = 760

/**
 * Round 7, My workouts, in [theme]: Free's with two saved days and its locked buttons; Pro's with two own workouts,
 * saved days and lists, and a list open; a class page with its heart filled, and the lists picker over it; the builder
 * with three exercises, its exercise picker and its sets dialog; an own workout's page, and Today while it runs; the
 * offers for the saved days, own workouts and lists.
 */
private fun libraryShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val ordered = NextClass.ordered(program.catalog)
    fun library(tier: Tier): AppModel {
        val store = MemoryStore()
        com.justdataplease.streetgym.app.data.AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), now = { NOW }, zone = TimeZone.UTC,
            settings = com.justdataplease.streetgym.app.data.AppSettings(store), library = LibraryStore(store),
            plans = shotPlans(store, tier), ads = FakeAds(), adStore = store).apply { this.theme = theme }
    }
    fun own(model: AppModel, title: String, vararg queries: String): String {
        val draft = model.newDraft()
        model.draft = draft.copy(name = title, exercises = queries.map { query ->
            val movement = model.exercises.search(query).first()
            CustomExercise(movement.key, movement.name, movement.movementId, movement.category, movement.equipment, List(3) { movement.sample })
        })
        model.saveDraft()
        return draft.id
    }

    val free = library(Tier.FREE)
    free.setSaved(ordered[0].id, true)
    free.setSaved(ordered[4].id, true)
    shot(File(out, "my-workouts-free-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Training, trainingTab = 1) }

    val pro = library(Tier.PRO)
    val push = own(pro, "Push at the park", "push-up", "dip", "pike")
    own(pro, "Hotel room legs", "pistol", "squat")
    listOf(0, 4, 9).forEach { pro.setSaved(ordered[it].id, true) }
    val travel = pro.createList("Travel")
    val rainy = pro.createList("Rainy days")
    pro.saveToLists(ordered[4].id, setOf(travel.id))
    pro.saveToLists(ordered[0].id, setOf(rainy.id))
    pro.saveToLists(push, setOf(travel.id))
    shot(File(out, "my-workouts-long-$name.png"), height = 6_000) { StreetGymApp(pro, SAFE_AREA, startAt = Screen.Training, trainingTab = 1) }
    shot(File(out, "my-workouts-list-$name.png")) {
        StreetGymApp(pro, SAFE_AREA, startAt = Screen.Training, trainingTab = 1, workoutList = travel.id)
    }
    // Today's class (the first: nothing is done yet), saved in Rainy days.
    shot(File(out, "class-saved-$name.png")) { StreetGymApp(pro, SAFE_AREA, startAt = Screen.Class(ordered[0].id)) }
    shot(File(out, "lists-picker-$name.png")) {
        OverDialog(pro, Screen.Class(ordered[0].id)) { ListPickerDialog(pro, ordered[0].id, {}, {}, {}) {} }
    }
    shot(File(out, "own-workout-long-$name.png"), height = 7_000) { StreetGymApp(pro, SAFE_AREA, startAt = Screen.Class(push)) }

    // The builder: the push workout open for editing.
    pro.editDraft(push)
    shot(File(out, "builder-$name.png")) { StreetGymApp(pro, SAFE_AREA, startAt = Screen.EditDay(push)) }
    pro.editDraft(push)
    shot(File(out, "builder-picker-$name.png")) {
        OverDialog(pro, Screen.EditDay(push)) { com.justdataplease.streetgym.app.ui.ExercisePickerDialog(pro, {}) {} }
    }
    pro.editDraft(push)
    val first = pro.customDay(push)!!.exercises.first()
    shot(File(out, "builder-sets-$name.png")) {
        OverDialog(pro, Screen.EditDay(push)) { com.justdataplease.streetgym.app.ui.SetsDialog(first.name, first.sets, {}) {} }
    }

    // Today while the own workout runs; the offers My workouts opens below Pro.
    val running = library(Tier.PRO).also { model ->
        val id = own(model, "Push at the park", "push-up", "dip", "pike")
        model.start(id)
    }
    shot(File(out, "today-own-$name.png")) { StreetGymApp(running, SAFE_AREA) }
    shot(File(out, "offer-saved-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Training, trainingTab = 1,
        showing = Shown.Offer(com.justdataplease.streetgym.app.data.Paywall.Saved(3))) }
    shot(File(out, "offer-custom-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Training, trainingTab = 1,
        showing = Shown.Offer(com.justdataplease.streetgym.app.data.Paywall.Custom)) }
    shot(File(out, "offer-lists-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Training, trainingTab = 1,
        showing = Shown.Offer(com.justdataplease.streetgym.app.data.Paywall.Lists(3))) }
}

/**
 * Round 8, Training › Library, in [theme]: the top of the list and the whole of it, filtered, with nothing left, on Plus
 * with the Pro tags and its offer, a ready workout's page, and a locked one's.
 */
private fun readyShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val athlete = athlete(program).apply { this.theme = theme }
    val library = Screen.Training
    shot(File(out, "library-$name.png")) { StreetGymApp(athlete, SAFE_AREA, startAt = library, trainingTab = TRAINING_LIBRARY) }
    shot(File(out, "library-long-$name.png"), height = LONG_HEIGHT) {
        StreetGymApp(athlete, SAFE_AREA, startAt = library, trainingTab = TRAINING_LIBRARY)
    }
    shot(File(out, "library-filtered-$name.png")) {
        StreetGymApp(athlete, SAFE_AREA, startAt = library, trainingTab = TRAINING_LIBRARY, readyFilter = ReadyFilter(grade = 1, focus = "legs"))
    }
    shot(File(out, "library-none-$name.png")) {
        StreetGymApp(athlete, SAFE_AREA, startAt = library, trainingTab = TRAINING_LIBRARY,
            readyFilter = ReadyFilter(effort = Effort.HARD, grade = 1, focus = "mobility"))
    }
    val plus = athlete(program, tier = Tier.PLUS).apply { this.theme = theme }
    shot(File(out, "library-plus-long-$name.png"), height = 9_000) {
        StreetGymApp(plus, SAFE_AREA, startAt = library, trainingTab = TRAINING_LIBRARY, readyFilter = ReadyFilter(focus = "problems"))
    }
    shot(File(out, "offer-ready-$name.png")) {
        StreetGymApp(plus, SAFE_AREA, startAt = library, trainingTab = TRAINING_LIBRARY, showing = Shown.Offer(Paywall.Ready))
    }
    val ready = program.catalog.workouts.first { !it.free && it.group == "full" }
    shot(File(out, "ready-class-long-$name.png"), height = LONG_HEIGHT) { StreetGymApp(athlete, SAFE_AREA, startAt = Screen.Class(ready.id)) }
    shot(File(out, "ready-locked-$name.png")) { StreetGymApp(plus, SAFE_AREA, startAt = Screen.Class(ready.id)) }
}

/** A Timer on the pictures' fixed clock, over [store], with alerts [alerts] as the phone says. */
private fun shotTiming(store: MemoryStore, clock: ShotClock, alerts: Alerts = Alerts.ALLOWED, hour24: Boolean = true): Timing =
    Timing(TimingStore(store, WallClock { clock.time }), object : WorkoutDevice {
        override fun alerts(result: (Alerts) -> Unit) = result(alerts)
    }, { TimeZone.UTC }, hour24)

/**
 * Round 8, the Timer tab, in [theme]: the timer as it opens and counting down, with alerts off, its duration's picker;
 * the stopwatch with laps; the alarm set, on a 12-hour clock with alerts off, its time's picker; "Time's up" over Today;
 * and the timer in landscape.
 */
private fun timerShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    fun timer(alerts: Alerts = Alerts.ALLOWED, hour24: Boolean = true, setUp: (Timing, ShotClock) -> Unit = { _, _ -> }): AppModel {
        val clock = ShotClock(NOW - 10 * MINUTE)
        val store = MemoryStore()
        val timing = shotTiming(store, clock, alerts, hour24)
        timing.refreshAlerts()
        setUp(timing, clock)
        clock.time = NOW
        timing.tick()
        com.justdataplease.streetgym.app.data.AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), now = { clock.time }, zone = TimeZone.UTC,
            settings = com.justdataplease.streetgym.app.data.AppSettings(store), timing = timing).apply { this.theme = theme }
    }
    shot(File(out, "timer-$name.png")) { StreetGymApp(timer(), SAFE_AREA, startAt = Screen.Timer) }
    val running = timer { timing, clock ->
        clock.time = NOW - 18_300L
        timing.timerPrimary()
    }
    shot(File(out, "timer-running-$name.png")) { StreetGymApp(running, SAFE_AREA, startAt = Screen.Timer) }
    shot(File(out, "timer-alerts-off-$name.png")) { StreetGymApp(timer(Alerts.NOT_ASKED), SAFE_AREA, startAt = Screen.Timer) }
    val picker = timer()
    shot(File(out, "timer-picker-$name.png")) {
        OverDialog(picker, Screen.Timer) { com.justdataplease.streetgym.app.ui.DurationDialog(90_000L, {}) {} }
    }
    val watch = timer { timing, clock ->
        timing.selectTab(1)
        clock.time = NOW - 95_420L
        timing.stopwatchPrimary()
        for (lap in listOf(31_220L, 30_480L, 29_950L)) {
            clock.time += lap
            timing.stopwatchSecondary()
        }
    }
    shot(File(out, "stopwatch-$name.png")) { StreetGymApp(watch, SAFE_AREA, startAt = Screen.Timer) }
    val alarm = timer { timing, _ ->
        timing.selectTab(2)
        timing.setAlarmTime(6, 30)
        timing.alarmPrimary()
    }
    shot(File(out, "alarm-$name.png")) { StreetGymApp(alarm, SAFE_AREA, startAt = Screen.Timer) }
    val twelve = timer(Alerts.NOT_ASKED, hour24 = false) { timing, _ -> timing.selectTab(2) }
    shot(File(out, "alarm-12h-alerts-off-$name.png")) { StreetGymApp(twelve, SAFE_AREA, startAt = Screen.Timer) }
    shot(File(out, "alarm-picker-$name.png")) {
        OverDialog(twelve, Screen.Timer) { com.justdataplease.streetgym.app.ui.AlarmTimeDialog(18, 45, hour24 = false, onClose = {}) { _, _ -> } }
    }
    val rang = timer { timing, clock ->
        timing.timerPrimary()
        clock.time += 61_000L
        timing.checkDue()
    }
    shot(File(out, "timer-ringing-$name.png")) { StreetGymApp(rang, SAFE_AREA) }
    shot(File(out, "timer-landscape-$name.png"), width = HEIGHT, height = WIDTH) {
        StreetGymApp(running, PaddingValues(start = 62.dp, end = 62.dp, bottom = 21.dp), startAt = Screen.Timer)
    }
}

/**
 * Round 9, Training › Parks, in [theme]: Free's example under its layer (the stand-in map, and the cards where no map
 * starts) and its offer; Plus loading, the map on the athlete with a park's card, the nearest as a list, a search's
 * matches, and with no position yet.
 */
private fun parksShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val free = athlete(program, tier = Tier.FREE).apply { this.theme = theme }
    shot(File(out, "parks-free-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Training, trainingTab = TRAINING_PARKS) }
    shot(File(out, "parks-free-cards-$name.png")) {
        StreetGymApp(free, SAFE_AREA, startAt = Screen.Training, trainingTab = TRAINING_PARKS, maps = null)
    }
    shot(File(out, "offer-parks-$name.png")) {
        StreetGymApp(free, SAFE_AREA, startAt = Screen.Training, trainingTab = TRAINING_PARKS, showing = Shown.Offer(Paywall.Parks))
    }

    fun plus(list: Boolean, setUp: (AppModel) -> Unit = {}): AppModel {
        val store = MemoryStore()
        val settings = com.justdataplease.streetgym.app.data.AppSettings(store)
        settings.placementDone = true
        settings.parksList = list
        return AppModel(program, StoredProgress(store), now = { NOW }, zone = TimeZone.UTC, settings = settings,
            location = PhoneLocation.Fixed(PhoneLocation.ATHENS.copy(time = NOW)), plans = shotPlans(store, Tier.PLUS)).apply {
            this.theme = theme
            runBlocking { parks.load() }
            setUp(this)
        }
    }
    val loading = AppModel(program, StoredProgress(MemoryStore()), tier = Tier.PLUS, now = { NOW }, zone = TimeZone.UTC,
        parksSource = object : com.justdataplease.streetgym.app.data.ParksSource {
            override suspend fun load() = kotlinx.coroutines.awaitCancellation()
        }).apply { this.theme = theme }
    shot(File(out, "parks-loading-$name.png")) { StreetGymApp(loading, SAFE_AREA, startAt = Screen.Training, trainingTab = TRAINING_PARKS) }

    // The map opens, finds the athlete and frames the five nearest (Android: renderParks, locateForParks); then a pin's card.
    val map = plus(list = false)
    shot(File(out, "parks-map-$name.png")) { StreetGymApp(map, SAFE_AREA, startAt = Screen.Training, trainingTab = TRAINING_PARKS) }
    map.parks.card = map.parks.results?.get(1)?.park
    shot(File(out, "parks-map-card-$name.png")) { StreetGymApp(map, SAFE_AREA, startAt = Screen.Training, trainingTab = TRAINING_PARKS) }
    val list = plus(list = true) { it.parks.locate() }
    shot(File(out, "parks-list-long-$name.png"), height = 9_000) {
        StreetGymApp(list, SAFE_AREA, startAt = Screen.Training, trainingTab = TRAINING_PARKS)
    }
    val search = plus(list = true) {
        it.parks.typed = "Berlin Workout park"
        it.parks.search("Berlin Workout park")
    }
    shot(File(out, "parks-search-$name.png")) { StreetGymApp(search, SAFE_AREA, startAt = Screen.Training, trainingTab = TRAINING_PARKS) }
    val nowhere = plus(list = true)
    shot(File(out, "parks-list-$name.png")) { StreetGymApp(nowhere, SAFE_AREA, startAt = Screen.Training, trainingTab = TRAINING_PARKS) }
}

/**
 * Round 10, Plans and purchases, in [theme]: Settings' plans on Free, Plus, the yearly Pro and Pro for life, a payment
 * waiting and the store still connecting; the offers of Level 4 and Level 7, after Plus is bought, Remove ads and the
 * leaderboard; Free's leaderboard under its layer and Plus's; the developer settings with Simulate tier.
 */
private fun plansShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val ordered = NextClass.ordered(program.catalog)
    fun plans(purchases: FakeStore = FakeStore(), setUp: (AppModel) -> Unit = {}): AppModel {
        val store = MemoryStore()
        com.justdataplease.streetgym.app.data.AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), now = { NOW }, zone = TimeZone.UTC,
            settings = com.justdataplease.streetgym.app.data.AppSettings(store), plans = shotPlans(store, Tier.FREE, purchases),
            ads = FakeAds(), adStore = store).apply {
            this.theme = theme
            setUp(this)
        }
    }
    val free = plans()
    shot(File(out, "settings-plans-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Settings) }
    shot(File(out, "settings-plans-long-$name.png"), height = 12_000) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Settings) }
    val plus = plans { it.plans.buy(Products.PLUS) }
    shot(File(out, "settings-plans-plus-long-$name.png"), height = 5_400) { StreetGymApp(plus, SAFE_AREA, startAt = Screen.Settings) }
    val yearly = plans { it.plans.buy(Products.PRO) }
    shot(File(out, "settings-plans-yearly-long-$name.png"), height = 5_400) { StreetGymApp(yearly, SAFE_AREA, startAt = Screen.Settings) }
    val life = plans { it.plans.buy(Products.PRO_LIFE) }
    shot(File(out, "settings-plans-life-long-$name.png"), height = 5_400) { StreetGymApp(life, SAFE_AREA, startAt = Screen.Settings) }
    val pending = plans(FakeStore().apply { next = com.justdataplease.streetgym.app.data.Bought.PENDING }) { it.plans.buy(Products.PLUS) }
    shot(File(out, "settings-plans-pending-long-$name.png"), height = 5_400) { StreetGymApp(pending, SAFE_AREA, startAt = Screen.Settings) }
    val connecting = run {
        val store = MemoryStore()
        val waiting = object : com.justdataplease.streetgym.app.data.Store by com.justdataplease.streetgym.app.data.Store.None {
            override val status = kotlinx.coroutines.flow.MutableStateFlow(com.justdataplease.streetgym.app.data.StoreStatus.LOADING)
        }
        AppModel(program, StoredProgress(store), now = { NOW }, zone = TimeZone.UTC, plans = Plans(store, waiting)).apply { this.theme = theme }
    }
    shot(File(out, "settings-plans-connecting-long-$name.png"), height = 5_400) { StreetGymApp(connecting, SAFE_AREA, startAt = Screen.Settings) }

    // The offers: Level 4 on Free (Today's Unlock), Level 7, Level 7 once Plus is bought, Remove ads, the leaderboard.
    val veteran = plans { it.progress.completePrograms(ordered.take(300).map { p -> p.id }, at = NOW - DAY) }
    shot(File(out, "offer-plus-long-$name.png"), height = 5_000) { StreetGymApp(veteran, SAFE_AREA, showing = Shown.Offer(Paywall.Level(4))) }
    shot(File(out, "offer-pro-$name.png")) { StreetGymApp(veteran, SAFE_AREA, startAt = Screen.Level(7), showing = Shown.Offer(Paywall.Level(7))) }
    shot(File(out, "offer-ads-$name.png")) { StreetGymApp(veteran, SAFE_AREA, showing = Shown.Offer(Paywall.Ads)) }
    val bought = plans { it.plans.buy(Products.PLUS) }
    shot(File(out, "offer-after-plus-$name.png")) { StreetGymApp(bought, SAFE_AREA, startAt = Screen.Level(7), showing = Shown.Offer(Paywall.Level(7))) }
    shot(File(out, "leaderboard-free-$name.png")) {
        StreetGymApp(free, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD)
    }
    shot(File(out, "offer-leaderboard-$name.png")) {
        StreetGymApp(free, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD, showing = Shown.Offer(Paywall.Leaderboard))
    }
    shot(File(out, "leaderboard-plus-$name.png")) {
        StreetGymApp(plus, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD)
    }
    shot(File(out, "developer-simulate-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Settings, showing = Shown.Developer) }
}

/**
 * Round 11's ads, in [theme], on Free: Today with Remove ads in the title bar and the card closing it; a level's classes
 * with the card after the third; a class page with cards between its sections; the list workout; Skills with the card
 * after the fourth ladder; and the Timer. The cards are the desktop's labelled stand-ins.
 */
private fun adsShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val free = athlete(program, tier = Tier.FREE).apply {
        this.theme = theme
        adConsent.gather()
    }
    val next = free.today().next!!.id
    shot(File(out, "ads-today-long-$name.png"), height = 5_400) { StreetGymApp(free, SAFE_AREA) }
    shot(File(out, "ads-level-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Level(1)) }
    shot(File(out, "ads-class-long-$name.png"), height = LONG_HEIGHT) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Class(next)) }
    shot(File(out, "ads-timer-$name.png")) { StreetGymApp(free, SAFE_AREA, startAt = Screen.Timer) }
    val ranked = ranked(program, Tier.FREE).apply {
        this.theme = theme
        adConsent.gather()
    }
    shot(File(out, "ads-skills-long-$name.png"), height = 9_000) { StreetGymApp(ranked, SAFE_AREA, startAt = Screen.Progress) }
    val list = athlete(program, tier = Tier.FREE).apply {
        this.theme = theme
        adConsent.gather()
        workoutView = WorkoutView.LIST
        start(next)
    }
    shot(File(out, "ads-workout-list-long-$name.png"), height = LONG_HEIGHT) { StreetGymApp(list, SAFE_AREA, startAt = Screen.Workout(next)) }
}

/** [screen] of [model] with [dialog] over it, drawn as the shell draws its layers. */
@Composable
private fun OverDialog(model: AppModel, screen: Screen, dialog: @Composable () -> Unit) {
    val overlay = remember { com.justdataplease.streetgym.app.ui.Overlay() }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        StreetGymApp(model, SAFE_AREA, startAt = screen)
        CompositionLocalProvider(LocalMotionLibrary provides model.motions) {
            StreetGymTheme(model.theme) {
                CompositionLocalProvider(com.justdataplease.streetgym.app.ui.LocalOverlay provides overlay,
                    com.justdataplease.streetgym.app.ui.LocalSafeArea provides SAFE_AREA) {
                    dialog()
                    com.justdataplease.streetgym.app.ui.OverlayLayers(overlay)
                }
            }
        }
    }
}

/** Brand cards with the park of each (day, story time); a null time holds the final skill. */
@Composable
private fun ParkSheet(frames: List<Pair<Long, Float?>>) {
    val theme = Street.theme
    val palette = theme.palette
    Column(Modifier.fillMaxSize().pageBackground(theme).padding(20.dp)) {
        for ((day, time) in frames) {
            CompositionLocalProvider(LocalParkTime provides time) {
                com.justdataplease.streetgym.app.ui.StreetCard(fill = palette.primaryDark, outlined = false, modifier = Modifier.padding(bottom = 12.dp)) {
                    BodyText("day $day · ${ParkRoutine.forEpochDay(day).moves.joinToString(", ") { it.name.lowercase() }}" +
                        (time?.let { " · $it s" } ?: " · final"), 12f, color = palette.heroKicker)
                    ParkScene(day, 0, Modifier.padding(top = 4.dp).fillMaxWidth().height(180.dp))
                }
            }
        }
    }
}

/**
 * An athlete with every rank: a dead hang at Rank 5, pull-ups at Rank 4, archer pull-ups at Rank 3, push-ups at Rank 2,
 * a tuck front lever at Rank 1 and a few wall handstands begun, from twelve own days of logged sets; on [tier].
 */
private fun ranked(program: Program, tier: Tier = Tier.PRO): AppModel {
    val book = program.skills!!
    fun sets(id: String, level: SkillLevel, count: Int): Pair<String, List<Int>> =
        id to List(count) { book.skill(id)!!.standard.target(level)!! }
    val logged = listOf(
        sets("dead_hang", SkillLevel.ELITE, 30), sets("pullup", SkillLevel.MASTER, 25), sets("archer_pullup", SkillLevel.ADVANCED, 22),
        sets("pushup", SkillLevel.INTERMEDIATE, 16), sets("tuck_front_hold", SkillLevel.BEGINNER, 12), sets("wall_handstand", SkillLevel.BEGINNER, 4),
    )
    val sessions = logged.mapIndexed { index, (id, doses) ->
        val at = NOW - (logged.size - index) * DAY
        WorkoutSession("custom:shots-$index", at - 40 * MINUTE, at, 2_400L, effortRating = 6, credits = mapOf(id to doses.size),
            doses = mapOf(id to doses), points = 180)
    }
    val store = MemoryStore()
    store.set("streetgym_progress.session_history", StoredProgress.encodeSessions(sessions))
    return AppModel(program, StoredProgress(store), now = { NOW }, zone = TimeZone.UTC, plans = shotPlans(store, tier),
        ads = FakeAds(), adStore = store)
}

/**
 * The athlete of the pictures with a fuller history: their twelve classes, the first repeated today, the tenth
 * done Harder, and on 4 November five classes marked complete and one on the 6th; a month earlier, three classes.
 */
private fun historyAthlete(program: Program): AppModel {
    val ordered = NextClass.ordered(program.catalog)
    val store = MemoryStore()
    val model = AppModel(program, StoredProgress(store), now = { NOW }, zone = TimeZone.UTC)
    val october = listOf(45, 44, 42)
    val daysAgo = listOf(17, 16, 14, 13, 11, 10, 8, 7, 4, 3, 2, 0)
    (october + daysAgo).forEachIndexed { index, day ->
        val finished = NOW - day * DAY - (if (day == 0) 8 * 60 * MINUTE else 0L)
        val harder = index == 12
        model.progress.startWorkout(ordered[index].id, startedAtEpochMillis = finished - 40 * MINUTE,
            intensity = if (harder) com.justdataplease.streetgym.data.progress.Intensity.HARDER else com.justdataplease.streetgym.data.progress.Intensity.NORMAL)
        model.progress.finishWorkout(completedAtEpochMillis = finished, effortRating = 6, points = 120)
    }
    model.progress.completePrograms(ordered.subList(15, 20).map { it.id }, at = NOW - 14 * DAY + 2 * 60 * MINUTE)
    model.progress.completePrograms(listOf(ordered[20].id), at = NOW - 12 * DAY)
    model.progress.startWorkout(ordered[0].id, NOW - 7 * 60 * MINUTE, repeat = true)
    model.progress.finishWorkout(NOW - 6 * 60 * MINUTE - 25 * MINUTE, effortRating = 5, points = 90)
    return model
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

private fun model(program: Program, clock: ShotClock = ShotClock(), tier: Tier = Tier.PRO): AppModel {
    val store = MemoryStore()
    return AppModel(program, StoredProgress(store), now = { clock.time }, zone = TimeZone.UTC, plans = shotPlans(store, tier),
        ads = FakeAds(), adStore = store)
}

/**
 * The plan of the pictures: [tier] whatever is bought, from a fake store at Android's prices that has answered, so
 * the offers show their prices; Simulate tier offered, as in a debug build.
 */
private fun shotPlans(store: MemoryStore, tier: Tier, purchases: FakeStore = FakeStore()): Plans =
    Plans(store, purchases.also { it.connect() }, canSimulate = true, floor = tier)

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


/**
 * Rounds 11 to 13's widget, in [theme]: the medium widget on the home screen, as the snapshot Swift draws it from, for
 * the athlete of the pictures (today's flame lit, then tomorrow's unlit and its end), a new athlete, an exam, a class
 * beyond Free and every class done.
 */
private fun widgetShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    val ordered = NextClass.ordered(program.catalog)
    val words = runBlocking { com.justdataplease.streetgym.app.data.WidgetWords.load() }
    val athlete = athlete(program).apply { this.theme = theme }
    val new = model(program).apply { this.theme = theme }
    val exam = model(program).apply {
        this.theme = theme
        progress.completePrograms(ordered.take(ordered.indexOfFirst { it.isExam }).map { it.id }, at = NOW - 30 * DAY)
    }
    val locked = model(program, tier = Tier.FREE).apply {
        this.theme = theme
        progress.completePrograms(ordered.take(300).map { it.id }, at = NOW - 30 * DAY)
    }
    val done = model(program).apply {
        this.theme = theme
        progress.completePrograms(ordered.map { it.id }, at = NOW - 30 * DAY)
    }
    val lit = athlete.widgetSnapshot(words)
    val rows = listOf(lit, lit, lit, new.widgetSnapshot(words), exam.widgetSnapshot(words), locked.widgetSnapshot(words), done.widgetSnapshot(words))
    val flames = listOf(0, 1, lit.flames.lastIndex, 0, 0, 0, 0)
    shot(File(out, "widget-$name.png"), width = 1_212, height = 4_020) {
        CompositionLocalProvider(LocalMotionLibrary provides program.motions) {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val pictures = rows.map { row ->
                remember(row) {
                    com.justdataplease.streetgym.app.ui.widgetFigure(row.card, row.theme, program.motions!!, emptyMap(), measurer,
                        androidx.compose.ui.text.font.FontFamily.Default)
                }
            }
            com.justdataplease.streetgym.app.ui.WidgetSheet(rows.zip(pictures), flames)
        }
    }
}

/**
 * Rounds 12 and 13 in [theme], over a fake Firebase: Settings' profile before a username, with one kept without an
 * account, connected on Pro with its last sync, the profile's dialog and Delete account's question, Your data, the
 * leaderboard with its boards and the athlete's row, reporting or blocking a name, and Today's park greeting the name.
 */
private fun accountShots(program: Program, theme: StreetTheme, out: File) {
    val name = theme.key
    fun phone(tier: Tier): Pair<AppModel, FakeCloud> {
        val clock = ShotClock()
        val store = MemoryStore()
        val account = FakeAccount()
        val cloud = FakeCloud({ account.user.value }, { clock.time })
        com.justdataplease.streetgym.app.data.AppSettings(store).placementDone = true
        val model = AppModel(program, StoredProgress(store), now = { clock.time }, zone = TimeZone.UTC,
            settings = com.justdataplease.streetgym.app.data.AppSettings(store), library = LibraryStore(store),
            plans = shotPlans(store, tier), ads = FakeAds(), adStore = store,
            services = com.justdataplease.streetgym.app.data.PhoneServices(account = account, documents = cloud, region = "GR"))
        val ordered = NextClass.ordered(program.catalog)
        listOf(17, 16, 14, 13, 11, 10, 8, 7, 4, 3, 2, 0).forEachIndexed { index, daysAgo ->
            val finished = NOW - daysAgo * DAY - (if (daysAgo == 0) 8 * 60 * MINUTE else 0L)
            model.progress.startWorkout(ordered[index].id, startedAtEpochMillis = finished - 40 * MINUTE)
            model.progress.finishWorkout(completedAtEpochMillis = finished, effortRating = 6, points = 120)
        }
        model.theme = theme
        return model to cloud
    }
    fun pick(model: AppModel, username: String): String {
        var uid = ""
        model.cloud.pickUsername { uid = it }
        model.cloud.saveProfile(uid, com.justdataplease.streetgym.app.data.Profile(username, "GR"))
        return uid
    }

    val (fresh, _) = phone(Tier.PLUS)
    shot(File(out, "settings-profile-new-$name.png")) { StreetGymApp(fresh, SAFE_AREA, startAt = Screen.Settings) }
    val (kept, _) = phone(Tier.FREE)
    pick(kept, "Alexa_7")
    shot(File(out, "settings-profile-$name.png")) { StreetGymApp(kept, SAFE_AREA, startAt = Screen.Settings) }
    val (pro, _) = phone(Tier.PRO)
    pick(pro, "Alexa_7")
    pro.cloud.connect(com.justdataplease.streetgym.app.data.SignInProvider.APPLE)
    shot(File(out, "settings-account-long-$name.png"), height = 13_500) { StreetGymApp(pro, SAFE_AREA, startAt = Screen.Settings) }
    shot(File(out, "delete-account-$name.png")) { StreetGymApp(pro, SAFE_AREA, startAt = Screen.Settings, showing = Shown.DeleteAccount) }
    val (editing, _) = phone(Tier.PLUS)
    val uid = pick(editing, "Alexa_7")
    shot(File(out, "profile-dialog-$name.png")) { StreetGymApp(editing, SAFE_AREA, startAt = Screen.Settings, showing = Shown.Profile(uid)) }
    // Today's park, held on its final skill, says "Well done, Alexa_7!".
    shot(File(out, "today-greeting-$name.png")) { StreetGymApp(editing, SAFE_AREA) }

    val (board, cloud) = phone(Tier.PLUS)
    pick(board, "Alexa_7")
    val points = board.points()
    val entries = listOf("Kalisthenis" to "GR", "maria.k" to "CY", "Bar_Wolf" to "DE", "Alexa_7" to "GR", "lena_runs" to "GR",
        "tomas" to "ES", "H4ndstand" to "US", "ella.moves" to "GB", "Nikos" to "GR", "pullup_pat" to "IE")
    val scores = listOf(48_200, 41_950, 33_400, points, points - 40, points - 120, points - 300, points - 500, points - 700, points - 900)
    cloud.documents["boards/all_world"] = mapOf(
        "entries" to entries.zip(scores).map { (who, score) -> mapOf("username" to who.first, "country" to who.second, "score" to score) },
        "players" to 1_234, "updated_at" to com.justdataplease.streetgym.app.data.CloudTime(NOW - 2 * 3_600_000L),
        "percentiles" to List(100) { 48_200 - it * 470 },
    )
    shot(File(out, "leaderboard-$name.png")) { StreetGymApp(board, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD) }
    shot(File(out, "leaderboard-long-$name.png"), height = 6_400) {
        StreetGymApp(board, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD)
    }
    shot(File(out, "leaderboard-report-$name.png")) {
        StreetGymApp(board, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD,
            showing = Shown.Report(com.justdataplease.streetgym.app.data.BoardEntry("Bar_Wolf", "DE", 33_400), "all_world"))
    }
    board.cloud.block("Bar_Wolf")
    shot(File(out, "leaderboard-blocked-long-$name.png"), height = 6_400) {
        StreetGymApp(board, SAFE_AREA, startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD)
    }
}
