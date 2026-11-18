package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.epochMillisNow
import com.justdataplease.streetgym.data.model.CurriculumCatalog
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.Streak
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillBook
import com.justdataplease.streetgym.data.skills.SkillCurriculumReader
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Instant

/** The program, the ready workouts and the skills, read once from the app's files. */
class Program(val catalog: CurriculumCatalog, val skills: SkillBook?)

/**
 * The program and the ready workouts from the app's files: the same `curriculum.json` and `workouts.json` as Android,
 * and the skills from the same JSON (Android: HomeActivity.loadCatalog). Without the skills a class still trains; it
 * credits no skill.
 */
suspend fun loadProgram(): Program = withContext(Dispatchers.Default) {
    val root = JSONObject(Res.readBytes("files/curriculum.json").decodeToString())
    val workouts = runCatching { JSONObject(Res.readBytes("files/workouts.json").decodeToString()).optJSONArray("workouts") }.getOrNull()
    val skills = runCatching { SkillCurriculumReader.read(root) }.getOrNull()
    Program(CurriculumJsonParser.parse(root, workouts), skills)
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

    /** Weeks start on Monday until the phone's region is read. */
    fun snapshot(sessions: List<WorkoutSession>, now: Long, zone: TimeZone, firstWeekday: Int = Streak.MONDAY): StreakSnapshot {
        val days = trained(sessions, zone)
        val today = epochDay(now, zone)
        return StreakSnapshot(Streak.of(days, today), Streak.week(days, today, firstWeekday))
    }
}

/** What Today shows. [next] is null once every class is complete. */
class Today(
    val next: TrainingProgram?,
    val level: TrainingLevel?,
    val resume: Boolean,
    val locked: Boolean,
    val done: Int,
    val total: Int,
    val streak: StreakSnapshot,
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

/**
 * The app's state over the shared engine: the program, the athlete's saved progress, and what each screen reads from
 * them. Screens call it while they draw, so a change here redraws them.
 *
 * [tier]: every level is open until purchases exist on iPhone (StoreKit), as in Android's development builds.
 * [skills] credits a finished class's sets to skills. [device] keeps the screen on and cues the end of a rest.
 */
@Stable
class AppModel(
    private val catalog: CurriculumCatalog,
    val progress: StoredProgress,
    val skills: SkillBook? = null,
    val tier: Tier = Tier.PRO,
    private val now: () -> Long = ::epochMillisNow,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    val device: WorkoutDevice = WorkoutDevice.None,
) {
    constructor(
        program: Program,
        progress: StoredProgress,
        tier: Tier = Tier.PRO,
        now: () -> Long = ::epochMillisNow,
        zone: TimeZone = TimeZone.currentSystemDefault(),
        device: WorkoutDevice = WorkoutDevice.None,
    ) : this(program.catalog, progress, program.skills, tier, now, zone, device)

    private val written: List<TrainingProgram> = NextClass.ordered(catalog)
    private val byId: Map<String, TrainingProgram> = written.associateBy(TrainingProgram::id)

    private val saves = Saves()

    /** The class in training: its sets, the rest timer and finishing it. */
    val training = Training(progress, skills, catalog.movementGrades, now, zone, device, saves) { id, intensity ->
        byId[id]?.let(intensity::scale)
    }

    var theme by mutableStateOf(StreetTheme.DEFAULT)

    /** Today's pick in "How do you feel today?". Back to Normal when the app restarts. */
    private var picked by mutableStateOf(Intensity.NORMAL)

    /**
     * Easier, Normal or Harder for the strength work: the class in progress's, else today's pick. A change applies to
     * the class in progress too, so its reps follow at once (Android: currentIntensity and setIntensity).
     */
    var intensity: Intensity
        get() = training.active()?.intensity ?: picked
        set(value) {
            picked = value
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
    fun isToday(id: String): Boolean = today().next?.id == id

    fun isCompleted(id: String): Boolean {
        saves.count
        return progress.isProgramCompleted(id)
    }

    fun today(): Today {
        saves.count
        val completed = progress.completedProgramIds()
        val active = progress.activeWorkout()
        val next = active?.programId?.let(byId::get) ?: NextClass.of(written, completed, replay = null)
        return Today(
            next = next?.let(intensity::scale),
            level = next?.let { level(it.levelNumber) },
            resume = active != null && active.programId == next?.id,
            locked = next != null && levelLocked(next.levelNumber),
            done = written.count { it.id in completed },
            total = written.size,
            streak = LocalDays.snapshot(progress.sessionHistory(), now(), zone),
        )
    }

    /** The class the sequence assigns, whatever is in progress (Android: assignedProgram). */
    private fun assigned(): TrainingProgram? = NextClass.of(written, progress.completedProgramIds(), replay = null)

    /** Today's class, in a level the tier opens (Android: canStart). */
    fun canStart(id: String): Boolean = assigned()?.id == id && byId[id]?.levelNumber?.let(::levelLocked) == false

    /** A completed class can be trained again, in a level the tier opens (Android: canRepeat). */
    fun canRepeat(id: String): Boolean = progress.isProgramCompleted(id) && byId[id]?.levelNumber?.let(::levelLocked) == false

    /**
     * Starts class [id], or with [repeat] a completed one again; the class already in progress is resumed. Another
     * class in progress is ended only with [replace] (Android: requestStart and its "Another workout is active").
     */
    fun start(id: String, repeat: Boolean = false, replace: Boolean = false): Start {
        if (!(if (repeat) canRepeat(id) else canStart(id))) return Start.LOCKED
        val current = progress.activeWorkout()
        if (current != null && current.programId == id && current.repeat == repeat) return Start.STARTED
        if (current != null) {
            if (!replace) return Start.ASK_REPLACE
            training.abandon()
        }
        progress.startWorkout(id, now(), repeat = repeat, intensity = picked)
        saves.bump()
        return Start.STARTED
    }

    /** "Complete day": marks the class done without training, so the sequence moves on. It earns nothing. */
    fun completeDay(id: String) {
        progress.completePrograms(listOf(id), at = now())
        training.skipRest()
        saves.bump()
    }
}
