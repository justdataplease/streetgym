package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Instant

/** The program and the ready workouts from the app's files: the same `curriculum.json` and `workouts.json` as Android. */
suspend fun loadCatalog(): CurriculumCatalog = withContext(Dispatchers.Default) {
    val root = JSONObject(Res.readBytes("files/curriculum.json").decodeToString())
    val workouts = runCatching { JSONObject(Res.readBytes("files/workouts.json").decodeToString()).optJSONArray("workouts") }.getOrNull()
    CurriculumJsonParser.parse(root, workouts)
}

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

/**
 * The app's state over the shared engine: the program, the athlete's saved progress, and what each screen reads from
 * them. Screens call it while they draw, so a change here redraws them.
 *
 * [tier]: every level is open until purchases exist on iPhone (StoreKit), as in Android's development builds.
 */
@Stable
class AppModel(
    private val catalog: CurriculumCatalog,
    val progress: StoredProgress,
    val tier: Tier = Tier.PRO,
    private val now: () -> Long = ::epochMillisNow,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val written: List<TrainingProgram> = NextClass.ordered(catalog)
    private val byId: Map<String, TrainingProgram> = written.associateBy(TrainingProgram::id)

    /** Counts the saves, so whatever read the progress is drawn again after one. */
    private var saves by mutableIntStateOf(0)

    var theme by mutableStateOf(StreetTheme.DEFAULT)

    /** "How do you feel today?": Easier, Normal or Harder for the strength work. Back to Normal when the app restarts. */
    var intensity by mutableStateOf(Intensity.NORMAL)

    /** A class as it is trained today, at [intensity]. */
    fun program(id: String): TrainingProgram? = byId[id]?.let(intensity::scale)

    fun level(number: Int): TrainingLevel? = catalog.levels.firstOrNull { it.number == number }

    fun levelLocked(number: Int): Boolean = LevelAccess.locked(number, tier)

    /** Whether [id] is the class the sequence offers now: the one that can be started or marked complete. */
    fun isToday(id: String): Boolean = today().next?.id == id

    fun isCompleted(id: String): Boolean {
        saves
        return progress.isProgramCompleted(id)
    }

    fun today(): Today {
        saves
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

    /** "Complete day": marks the class done without training, so the sequence moves on. It earns nothing. */
    fun completeDay(id: String) {
        progress.completePrograms(listOf(id), at = now())
        saves++
    }
}
