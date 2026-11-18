package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.justdataplease.streetgym.app.figure.AnimationLibrary
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentSwitches
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.points.Points
import com.justdataplease.streetgym.data.skills.SkillFamily
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillProgress
import com.justdataplease.streetgym.data.skills.SkillStatus
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.data.skills.switchedOff
import com.justdataplease.streetgym.ui.WorkoutFlow
import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.effort.Effort
import com.justdataplease.streetgym.data.effort.WorkoutEffort
import com.justdataplease.streetgym.data.epochMillisNow
import com.justdataplease.streetgym.data.model.CurriculumCatalog
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.ActiveWorkout
import com.justdataplease.streetgym.data.progress.DayAssignment
import com.justdataplease.streetgym.data.progress.ExamResult
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.LevelReplay
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.Streak
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillBook
import com.justdataplease.streetgym.data.skills.SkillCredit
import com.justdataplease.streetgym.data.skills.SkillCurriculumReader
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Instant

/** The version shown where the phone gives none (previews, tests). */
const val DEFAULT_VERSION = "1.0"

/** The program, the ready workouts, the skills and the line man's movements, read once from the app's files. */
class Program(val catalog: CurriculumCatalog, val skills: SkillBook?, val motions: AnimationLibrary? = null)

/**
 * The program and the ready workouts from the app's files: the same `curriculum.json` and `workouts.json` as Android,
 * and the skills from the same JSON (Android: HomeActivity.loadCatalog). Without the skills a class still trains; it
 * credits no skill. The movements (`exercise_animations.json`) are read with them, as Android preloads them with the
 * catalog; without them every figure stands as Android's fallback.
 */
suspend fun loadProgram(): Program = withContext(Dispatchers.Default) {
    val root = JSONObject(Res.readBytes("files/curriculum.json").decodeToString())
    val workouts = runCatching { JSONObject(Res.readBytes("files/workouts.json").decodeToString()).optJSONArray("workouts") }.getOrNull()
    val skills = runCatching { SkillCurriculumReader.read(root) }.getOrNull()
    val motions = runCatching { AnimationLibrary.read(Res.readBytes("files/exercise_animations.json").decodeToString()) }.getOrNull()
    Program(CurriculumJsonParser.parse(root, workouts), skills, motions)
}

/** The line man's movements alone. */
suspend fun loadMotions(): AnimationLibrary = withContext(Dispatchers.Default) {
    AnimationLibrary.read(Res.readBytes("files/exercise_animations.json").decodeToString())
}

/** The program alone. */
suspend fun loadCatalog(): CurriculumCatalog = loadProgram().catalog

/** The streak now, with the seven days of this week for the week row. */
class StreakSnapshot(val streak: Streak, val week: List<Streak.Day>)

/**
 * The [Streak] from this phone's history and clock. A day is the local date in the phone's time zone; it turns at
 * local midnight (Android: StreakDays).
 */
object LocalDays {
    private const val DAY_MILLIS = 86_400_000L

    /** The local calendar day of [millis] in [zone], as days since 1970-01-01. */
    fun epochDay(millis: Long, zone: TimeZone): Long =
        (millis + zone.offsetAt(Instant.fromEpochMilliseconds(millis)).totalSeconds * 1_000L).floorDiv(DAY_MILLIS)

    /** The local days with a workout finished ([Streak.counts]). */
    fun trained(sessions: List<WorkoutSession>, zone: TimeZone): Set<Long> =
        sessions.filter(Streak::counts).mapTo(HashSet()) { epochDay(it.completedAtEpochMillis, zone) }

    /** [firstWeekday]: 1 Monday to 7 Sunday ([Streak]'s numbering), where the phone's region starts its weeks. */
    fun snapshot(sessions: List<WorkoutSession>, now: Long, zone: TimeZone, firstWeekday: Int = Streak.MONDAY): StreakSnapshot {
        val days = trained(sessions, zone)
        val today = epochDay(now, zone)
        return StreakSnapshot(Streak.of(days, today), Streak.week(days, today, firstWeekday))
    }

    /**
     * A calendar's first weekday, 1 Sunday to 7 Saturday (java.util.Calendar's and NSCalendar's numbering), as [Streak]'s
     * 1 Monday to 7 Sunday (Android: StreakDays.isoWeekday).
     */
    fun isoWeekday(calendarDay: Int): Int = (calendarDay + 5) % 7 + 1
}

/** The classes that count for ranks and points: trained ones, never marked or placed (Android: Leaderboard.kt Scores.credits). */
object Scores {
    fun credits(sessions: List<WorkoutSession>): List<SkillCredit> = sessions
        .filter { !it.manuallyCompleted }
        .sortedBy { it.completedAtEpochMillis }
        .map { session -> SkillCredit(session.programId, session.credits, session.doses) }
}

/** The words over the class's title on Today (Android: renderDashboard's label). */
sealed interface TodayLabel {
    /** "Repeat: …": a repeat in progress. */
    data object Repeat : TodayLabel
    /** "Repeating for the exam": an exam's block repeated. */
    data object Exam : TodayLabel
    /** "Repeating from Level N": a level gone back to. */
    data class Replay(val level: Int) : TodayLabel
}

/**
 * What Today shows. [next] is null once every class is complete. [repeating]: the class in progress is a completed one
 * trained again, which Today resumes as a repeat. [canComplete]: Complete day is offered.
 */
class Today(
    val next: TrainingProgram?,
    val level: TrainingLevel?,
    val resume: Boolean,
    val locked: Boolean,
    val done: Int,
    val total: Int,
    val streak: StreakSnapshot,
    val repeating: Boolean = false,
    val label: TodayLabel? = null,
    val canComplete: Boolean = !repeating,
)

/** What asking to start a class did. */
enum class Start {
    /** The class is in progress: started now, or resumed. */
    STARTED,
    /** Another class is in progress; starting this one ends it, so the athlete is asked first. */
    ASK_REPLACE,
    /** Not a class that can start now: not today's, or behind a tier. */
    LOCKED,
}

/** Where a level stands for the athlete (Android: ThemeDecor.LevelState). */
enum class LevelState { DONE, CURRENT, UPCOMING }

/**
 * Why an exercise is left out of the athlete's classes (Android: HomeActivity.LeftOut): its skill switched off, named
 * by the family ([Skill]), or equipment their spot lacks ([Gear]).
 */
sealed interface LeftOut {
    data class Skill(val name: String) : LeftOut
    data class Gear(val equipment: Equipment) : LeftOut
}

/** A level's card on Training › Levels: [complete] of its classes passed; [locked] behind a tier. */
class LevelCard(val level: TrainingLevel, val state: LevelState, val complete: Int, val locked: Boolean)

/** Training › Levels: the replay in effect, the class the athlete stopped at, and every level. */
class Levels(val replay: LevelReplay?, val furthest: TrainingProgram?, val cards: List<LevelCard>)

/**
 * One class in its level's list (Android: ProgramListAdapter). [assigned]: today's; [off]: passed by the skill and
 * equipment switches; [exam]: an exam's latest result.
 */
class ClassRow(val program: TrainingProgram, val completed: Boolean, val assigned: Boolean, val off: Boolean, val exam: ExamResult?) {
    /** Reached: its details show. */
    val unlocked: Boolean get() = completed || assigned
}

/**
 * A level's page (Android: renderWorkouts). [complete]: classes completed or switched off. [current]: the open level of
 * today's class, which offers Complete level ([levelOneHint] on Level 1 outside a replay); [canGoBack]: an earlier
 * level, which offers to go back to it.
 */
class LevelPage(
    val level: TrainingLevel,
    val complete: Int,
    val locked: Boolean,
    val current: Boolean,
    val levelOneHint: Boolean,
    val canGoBack: Boolean,
    val rows: List<ClassRow>,
)

/**
 * The app's state over the shared engine: the program, the athlete's saved progress and choices, and what each screen
 * reads from them. Screens call it while they draw, so a change here redraws them.
 *
 * [tier]: every level is open until purchases exist on iPhone (StoreKit), as in Android's development builds.
 * [skills] credits a finished class's sets to skills. [device] keeps the screen on and cues the end of a rest.
 * [motions] are the line man's movements; [settings] keeps the athlete's choices. [firstWeekday] (1 Monday to 7
 * Sunday) starts the streak's week where the phone's region starts it.
 */
@Stable
class AppModel(
    private val catalog: CurriculumCatalog,
    val progress: StoredProgress,
    val skills: SkillBook? = null,
    val tier: Tier = Tier.PRO,
    private val now: () -> Long = ::epochMillisNow,
    /** The phone's time zone: days, and the dates shown. */
    val zone: TimeZone = TimeZone.currentSystemDefault(),
    val device: WorkoutDevice = WorkoutDevice.None,
    val motions: AnimationLibrary? = null,
    private val settings: AppSettings = AppSettings(MemoryStore()),
    val firstWeekday: Int = Streak.MONDAY,
) {
    constructor(
        program: Program,
        progress: StoredProgress,
        tier: Tier = Tier.PRO,
        now: () -> Long = ::epochMillisNow,
        zone: TimeZone = TimeZone.currentSystemDefault(),
        device: WorkoutDevice = WorkoutDevice.None,
        settings: AppSettings = AppSettings(MemoryStore()),
        firstWeekday: Int = Streak.MONDAY,
    ) : this(program.catalog, progress, program.skills, tier, now, zone, device, program.motions, settings, firstWeekday)

    private val written: List<TrainingProgram> = NextClass.ordered(catalog)
    private val byId: Map<String, TrainingProgram> = written.associateBy(TrainingProgram::id)

    private val saves = Saves()

    private var screenAwake by mutableStateOf(settings.keepScreenAwake)
    private var sounds by mutableStateOf(settings.soundCues)
    private var skillsOff by mutableStateOf(settings.skillSwitches)
    private var equipmentOff by mutableStateOf(settings.equipment)
    private var look by mutableStateOf(settings.theme)
    private var inspecting by mutableStateOf(settings.inspectAllDays)
    private var developer by mutableStateOf(settings.developerUnlocked)
    /**
     * Per class, the exercises the athlete chose to do anyway though switched off ("Continue"), by exercise key, while
     * the app runs; starting another class forgets them (Android: offAllowed).
     */
    private var allowed by mutableStateOf<Map<String, Set<String>>>(emptyMap())

    /** The class in training: its sets, the rest timer and finishing it. */
    val training = Training(
        progress, skills, catalog.movementGrades, now, zone, device, saves,
        sound = { sounds },
        leftOutOfExam = { exercise -> exercise.movementId?.let(skillSwitches::isOff) == true || equipment.isOff(exercise) },
        leftOut = { program, flow -> leftOutKeys(program, flow) },
    ) { id, intensity -> byId[id]?.let(intensity::scale) }

    /** The look picked in Settings, kept on the phone (Android: AppSettings.theme); every screen follows at once. */
    /** The app's version, as Settings › About shows it ("Version 1.0"). */
    var version: String = DEFAULT_VERSION

    /** The taps on the version line toward the developer settings, while the app runs. */
    val versionTaps = com.justdataplease.streetgym.app.ui.VersionTaps()

    var theme: StreetTheme
        get() = look
        set(value) {
            look = value
            settings.theme = value
        }

    /** Settings › Skills in your classes: what is switched off, kept under Android's key; the screens follow at once. */
    var skillSwitches: SkillSwitches
        get() = skillsOff
        set(value) {
            skillsOff = value
            settings.skillSwitches = value
            saves.bump()
        }

    /** Settings › Equipment you have: what the athlete's spot lacks. */
    var equipment: EquipmentSwitches
        get() = equipmentOff
        set(value) {
            equipmentOff = value
            settings.equipment = value
            saves.bump()
        }

    /** Developer settings › Inspect all days: classes not reached open read-only. */
    var inspectAllDays: Boolean
        get() = inspecting
        set(value) {
            inspecting = value
            settings.inspectAllDays = value
        }

    /** The developer settings were opened by ten taps on the version; one tap opens them again. */
    var developerUnlocked: Boolean
        get() = developer
        set(value) {
            developer = value
            settings.developerUnlocked = value
        }

    /**
     * The local day the screens were drawn for; [refreshDay] moves it on after midnight, which redraws Today's flame
     * and puts today's pick back to Normal (Android: onResume and streakDrawnOn).
     */
    var day by mutableLongStateOf(LocalDays.epochDay(now(), zone))
        private set

    /** Looks at the clock: true when a new local day began since the screens were drawn. */
    fun refreshDay(): Boolean {
        val today = LocalDays.epochDay(now(), zone)
        if (today == day) return false
        day = today
        return true
    }

    private var view by mutableStateOf(settings.workoutView)

    /**
     * Focus or List, as the athlete last picked it on a class page, in Settings or in the workout's title bar
     * (Android: AppSettings.workoutView); both show the same class in progress, so switching keeps every tick and the rest.
     */
    var workoutView: WorkoutView
        get() = view
        set(value) {
            view = value
            settings.workoutView = value
        }

    /** Settings › Keep screen awake (Android: AppSettings.keepScreenAwake): the list view keeps the screen on only with it. */
    var keepScreenAwake: Boolean
        get() = screenAwake
        set(value) {
            screenAwake = value
            settings.keepScreenAwake = value
        }

    /** Settings › Rest timer sound (Android: AppSettings.soundCues): the beeps at the end of a rest and of a timed set. */
    var soundCues: Boolean
        get() = sounds
        set(value) {
            sounds = value
            settings.soundCues = value
        }

    /** Today's pick in "How do you feel today?" and the local day it was made on (Android: AppSettings.intensityToday). */
    private var pick by mutableStateOf(settings.intensityToday(now(), zone) to LocalDays.epochDay(now(), zone))

    /** The day's pick: Normal again on a new local day. */
    private fun todayPick(): Intensity = pick.takeIf { it.second == day }?.first ?: Intensity.NORMAL

    /**
     * Easier, Normal or Harder for the strength work: the class in progress's, else today's pick, kept on the phone for
     * the day. A change applies to the class in progress too, so its reps follow at once (Android: currentIntensity and
     * setIntensity).
     */
    var intensity: Intensity
        get() = training.active()?.intensity ?: todayPick()
        set(value) {
            refreshDay()
            pick = value to day
            settings.setIntensityToday(value, now(), zone)
            if (progress.activeWorkout() != null) {
                progress.setIntensity(value)
                saves.bump()
            }
        }

    /** A class as it is trained today, at [intensity]. */
    fun program(id: String): TrainingProgram? = byId[id]?.let(intensity::scale)

    fun level(number: Int): TrainingLevel? = catalog.levels.firstOrNull { it.number == number }

    fun levelLocked(number: Int): Boolean = LevelAccess.locked(number, tier)

    /** Whether [id] is the class the sequence offers now: the one that can be started or marked complete. */
    fun isToday(id: String): Boolean {
        saves.count
        // The sequence's class, whatever is in progress: a repeat in progress is not today's (Android: assignedProgram).
        return assigned()?.id == id
    }

    fun isCompleted(id: String): Boolean {
        saves.count
        return progress.isProgramCompleted(id)
    }

    fun today(): Today {
        saves.count
        day
        val completed = progress.completedProgramIds()
        val active = progress.activeWorkout()
        // The class in progress, if it may run, else the sequence's (Android: renderDashboard).
        val next = active?.takeIf(::canRun)?.programId?.let(byId::get) ?: assigned()
        val repeating = active?.repeat == true && active.programId == next?.id
        val replay = levelReplay()
        return Today(
            next = next?.let(intensity::scale),
            level = next?.let { level(it.levelNumber) },
            resume = active != null && active.programId == next?.id,
            locked = next != null && levelLocked(next.levelNumber),
            done = written.count { it.id in completed },
            total = written.size,
            streak = LocalDays.snapshot(progress.sessionHistory(), now(), zone, firstWeekday),
            repeating = repeating,
            label = when {
                repeating -> TodayLabel.Repeat
                replay == null -> null
                replay.fromProgramId != null -> TodayLabel.Exam
                else -> TodayLabel.Replay(replay.levelNumber)
            },
            canComplete = next != null && canStart(next.id) && !repeating,
        )
    }

    /**
     * Classes completed, or left with nothing to train by the skill or equipment switches: the sequence steps over both
     * (Android: passedIds).
     */
    private fun passed(completed: Set<String> = progress.completedProgramIds()): Set<String> =
        NextClass.passed(written, completed, skillSwitches, equipment)

    /**
     * The level replay in effect, or null; one that has caught up with the furthest class ends here (Android:
     * levelReplay). Ending it changes nothing a screen shows, so it needs no redraw.
     */
    fun levelReplay(): LevelReplay? {
        saves.count
        val replay = progress.levelReplay() ?: return null
        if (DayAssignment.replaying(written, passed(), replay, TrainingProgram::id, TrainingProgram::levelNumber)) return replay
        progress.endLevelReplay()
        return null
    }

    /** The class the sequence assigns, whatever is in progress, a replay's included (Android: assignedProgram). */
    private fun assigned(): TrainingProgram? = NextClass.of(written, passed(), levelReplay())

    /** The first class never completed: where the athlete stopped, whatever they repeat (Android: furthestProgram). */
    private fun furthest(passed: Set<String> = passed()): TrainingProgram? = written.firstOrNull { it.id !in passed }

    /** Today's class, in a level the tier opens (Android: canStart). */
    fun canStart(id: String): Boolean = assigned()?.id == id && byId[id]?.levelNumber?.let(::levelLocked) == false

    /** A completed class can be trained again, in a level the tier opens (Android: canRepeat). */
    fun canRepeat(id: String): Boolean = progress.isProgramCompleted(id) && byId[id]?.levelNumber?.let(::levelLocked) == false

    /** The class in progress may go on: a repeat of a completed class, or today's (Android: canRun). */
    private fun canRun(active: ActiveWorkout): Boolean = if (active.repeat) canRepeat(active.programId) else canStart(active.programId)

    /**
     * Starts class [id], or with [repeat] a completed one again; the class already in progress is resumed. Another
     * class in progress is ended only with [replace] (Android: requestStart and its "Another workout is active").
     */
    fun start(id: String, repeat: Boolean = false, replace: Boolean = false): Start {
        if (!(if (repeat) canRepeat(id) else canStart(id))) return Start.LOCKED
        val current = progress.activeWorkout()
        if (current?.programId != id) allowed = allowed - id
        if (current != null && current.programId == id && current.repeat == repeat) return Start.STARTED
        if (current != null) {
            if (!replace) return Start.ASK_REPLACE
            training.abandon()
        }
        progress.startWorkout(id, now(), repeat = repeat, intensity = todayPick())
        saves.bump()
        return Start.STARTED
    }

    /** "Complete day": marks the class done without training, so the sequence moves on. It earns nothing. */
    fun completeDay(id: String) {
        progress.completePrograms(listOf(id), at = now())
        training.skipRest()
        saves.bump()
    }

    /** Training › Levels: every level with how far the athlete is through it (Android: renderLevels and levelCard). */
    fun levels(): Levels {
        saves.count
        val passed = passed()
        val current = assigned()?.levelNumber
        val cards = catalog.levels.sortedBy { it.number }.map { level ->
            val state = when {
                level.number == current -> LevelState.CURRENT
                level.programs.all { it.id in passed } -> LevelState.DONE
                else -> LevelState.UPCOMING
            }
            LevelCard(level, state, level.programs.count { it.id in passed }, levelLocked(level.number))
        }
        return Levels(levelReplay(), furthest(passed), cards)
    }

    /** A level's page and its classes (Android: renderWorkouts). */
    fun levelPage(number: Int): LevelPage? {
        saves.count
        val level = level(number) ?: return null
        val completed = progress.completedProgramIds()
        val passed = passed(completed)
        // Classes the switches leave with nothing to train count as passed, greyed in the list.
        val switchedOff = passed - completed
        val locked = levelLocked(number)
        val assigned = assigned()
        val exams = progress.examLog()
        val current = assigned?.levelNumber == number && !locked
        val rows = level.programs.sortedBy { it.number }.map { program ->
            ClassRow(program, program.id in completed, program.id == assigned?.id, program.id in switchedOff,
                if (program.isExam) exams.latest(program.id) else null)
        }
        return LevelPage(
            level = level,
            complete = level.programs.count { it.id in completed || it.id in switchedOff },
            locked = locked,
            current = current,
            levelOneHint = current && number == 1 && levelReplay() == null,
            // Any level before the furthest class can be trained again; nothing completed is undone.
            canGoBack = number < (furthest(passed)?.levelNumber ?: Int.MAX_VALUE) && assigned?.levelNumber != number && !locked,
            rows = rows,
        )
    }

    /** "Complete level": every class of [number] done without training (Android: renderWorkouts' Complete level). */
    fun completeLevel(number: Int) {
        val level = level(number) ?: return
        progress.completePrograms(level.programs.map(TrainingProgram::id), at = now())
        training.skipRest()
        saves.bump()
    }

    /** "Go back to this level": Today starts again at its first class (Android: requestLevelReplay). */
    fun goBackTo(number: Int) {
        progress.startLevelReplay(number)
        training.skipRest()
        saves.bump()
    }

    /** "Return to my furthest day" (Android: replayCard). */
    fun returnToFurthest() {
        progress.endLevelReplay()
        training.skipRest()
        saves.bump()
    }

    /** The latest attempt at exam class [id], or null when it was never taken. */
    fun examResult(id: String): ExamResult? {
        saves.count
        return progress.examLog().latest(id)
    }

    /**
     * Saves which of [question]'s targets were [reached] (Android: askExam's Save results), each by its movement as
     * Android keeps them.
     */
    fun saveExam(question: ExamQuestion, reached: List<Boolean>): ExamResult {
        val ids = question.tests.map { (exercise, _) -> exercise.movementId ?: exercise.id }
        val result = ExamResult(question.exam.id, now(), ids.filterIndexed { index, _ -> reached[index] },
            ids.filterIndexed { index, _ -> !reached[index] })
        progress.recordExam(result)
        saves.bump()
        return result
    }

    /** The classes from the one after the previous exam up to [exam]: the first and how many (Android: askRepeatBlock). */
    fun examBlock(exam: TrainingProgram): Pair<TrainingProgram, Int>? {
        val index = written.indexOfFirst { it.id == exam.id }.takeIf { it >= 0 } ?: return null
        val start = (index - 1 downTo 0).firstOrNull { written[it].isExam }?.plus(1) ?: 0
        return written[start] to index - start + 1
    }

    /** "Repeat N classes": Today goes back to the block's first class and comes through [exam] again. */
    fun repeatBlock(exam: TrainingProgram) {
        val (first, _) = examBlock(exam) ?: return
        progress.startLevelReplay(first.levelNumber, fromProgramId = first.id)
        training.skipRest()
        saves.bump()
    }

    // The athlete's workout rater and what it was made from: the history, the level and the day's intensity.
    private var effortCache: Pair<List<Any?>, WorkoutEffort>? = null

    /** Rates workouts for this athlete (Android: workoutEffort): the typical class of their level and their best sets. */
    private fun workoutEffort(): WorkoutEffort {
        val history = progress.sessionHistory()
        val levelNumber = assigned()?.levelNumber ?: catalog.levels.firstOrNull()?.number ?: 1
        val intensity = intensity
        val key = listOf(history.size, history.firstOrNull()?.completedAtEpochMillis, levelNumber, intensity)
        effortCache?.takeIf { it.first == key }?.let { return it.second }
        val bests = skills?.let { book -> book.progress(Scores.credits(history)).statuses.associate { it.skill.id to it.best } }.orEmpty()
        val classes = level(levelNumber)?.programs.orEmpty().map(intensity::scale)
        return WorkoutEffort(catalog.movementGrades, bests, levelNumber, classes).also { effortCache = key to it }
    }

    /** "Easy / Moderate / Hard for you" for class [id] as it is trained today (Android: effortOf). */
    fun effortOf(id: String): Effort? {
        saves.count
        return program(id)?.let { workoutEffort().rate(it) }
    }

    /**
     * The exercise class [id]'s tile plays: its hardest, a moving one before a hold of the same grade (Android:
     * EffortUi.tileExercise).
     */
    fun tileOf(id: String): TrainingExercise? {
        saves.count
        return program(id)?.let { program ->
            workoutEffort().hardest(program) { tiles.moves(it.movementId, it.name.resolve(), it.category, it.equipment) }
        }
    }

    private val tiles: AnimationLibrary by lazy { motions ?: AnimationLibrary.fallbackLibrary() }

    /**
     * The skill, or the equipment the athlete lacks, that leaves [exercise] (the [index]th of [section]) out of
     * [program]; null when it is in: on, or chosen anyway in this workout (Android: leftOutBy). An own day or a ready
     * workout runs as written.
     */
    fun leftOutBy(program: TrainingProgram, section: String, exercise: TrainingExercise, index: Int): LeftOut? {
        if (CustomDays.offProgram(program.id)) return null
        val family = exercise.movementId?.let(skillSwitches::offFamily)?.let { id -> SkillBook.DEFAULT_FAMILIES.firstOrNull { it.id == id } }
        val lacks = if (family == null) equipment.missing(exercise.equipment) else null
        if (family == null && lacks == null) return null
        if (WorkoutFlow.exerciseKey(section, exercise, index) in allowed[program.id].orEmpty()) return null
        return family?.let { LeftOut.Skill(it.name) } ?: lacks?.let(LeftOut::Gear)
    }

    /** The set keys of [flow] that the switches leave out (Android: leftOutKeys). */
    fun leftOutKeys(program: TrainingProgram, flow: WorkoutFlow): Set<String> =
        if (!skillSwitches.any && !equipment.any) emptySet()
        else flow.steps.filter { leftOutBy(program, it.section, it.exercise, it.exerciseIndex) != null }.mapTo(HashSet()) { it.key }

    /** "Continue" on "Handstand is switched off": the exercises with [exerciseKeys] count in this workout of [programId]. */
    fun allowAnyway(programId: String, exerciseKeys: Collection<String>) {
        allowed = allowed + (programId to (allowed[programId].orEmpty() + exerciseKeys))
    }

    /** A class the switches leave with nothing to train counts as passed, and its page says so (Android: renderOverview). */
    fun switchedOff(program: TrainingProgram): Boolean {
        saves.count
        return !CustomDays.offProgram(program.id) && program.switchedOff(skillSwitches, equipment) && !progress.isProgramCompleted(program.id)
    }

    /** Whether a movement needs equipment the athlete switched off (Android: lacksEquipment). */
    fun lacksEquipment(movementId: String): Boolean = equipment.missing(catalog.movementEquipment[movementId].orEmpty()) != null

    /** Every movement's equipment tags, to count the exercises each switch leaves out. */
    val movementEquipment: Map<String, List<String>> get() = catalog.movementEquipment

    /** Developer inspection opens a class not reached yet, or behind a tier, read-only (Android: renderOverview's inspecting). */
    fun inspects(id: String): Boolean {
        val program = byId[id] ?: return false
        return inspectAllDays && !isCompleted(id) && (levelLocked(program.levelNumber) || !canStart(id))
    }

    /** Every finished class and marked day, newest first. */
    fun history(): List<WorkoutSession> {
        saves.count
        return progress.sessionHistory()
    }

    /** The streak now, with this week's days (Android: StreakDays.snapshot). */
    fun streak(): StreakSnapshot {
        saves.count
        day
        return LocalDays.snapshot(progress.sessionHistory(), now(), zone, firstWeekday)
    }

    /** Today in the phone's time zone, as History's calendar keys its days (yyyymmdd). */
    fun todayKey(): Int {
        day
        return HistoryCalendar.dayKey(now(), zone)
    }

    /** A class of the program by its id, as written (History's rows). */
    fun programById(id: String): TrainingProgram? = byId[id]

    /** The athlete's skills: every ladder with each skill's rank, from trained classes only (Android: skillProgress). */
    fun skillProgress(): SkillProgress? {
        saves.count
        return skills?.progress(Scores.credits(progress.sessionHistory()))
    }

    /** Points on Skills: every class's, plus the ranks' (Android: pointsTotal.all). */
    fun points(): Int {
        val history = history()
        return Points.classes(history) + (skillProgress()?.let { Points.chevrons(it.statuses) } ?: 0)
    }

    /**
     * The tier a skill waits for: one the program first teaches in a level the tier doesn't open stays locked until it
     * has a rank (Android: SkillsScreen.lockFor). Null when it is open.
     */
    fun lockFor(status: SkillStatus): Tier? =
        status.skill.expected?.level?.takeIf { status.level == SkillLevel.NONE && levelLocked(it) }?.let(LevelAccess::tierFor)

    /** A skill switched off from its rung, or whose movement needs equipment switched off: its medal greys. */
    fun skillOff(family: SkillFamily, status: SkillStatus): Boolean =
        skillSwitches.isRungOff(family.spec.id, status.skill.rung) || lacksEquipment(status.skill.id)

    /**
     * Settings' Start over (Android: confirmReset): every finished class, the history, points, ranks and exam results
     * go, with the class in progress; the strength test is asked again. My workouts, the theme and the purchases stay.
     */
    fun reset() {
        training.reset()
        progress.reset(now())
        settings.placementDone = false
        saves.bump()
    }
}
