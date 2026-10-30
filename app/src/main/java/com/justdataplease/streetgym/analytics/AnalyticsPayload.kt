package com.justdataplease.streetgym.analytics

import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.ActiveWorkout
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillProgress
import com.justdataplease.streetgym.ui.WorkoutFlow
import com.justdataplease.streetgym.ui.planned
import com.justdataplease.streetgym.ui.timed
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The install's training as the app sees it when it syncs ([AnalyticsPayload.install]). [tier] is the tier the app
 * believes (Pro in debug builds, [debug] says so). [skills]: each started skill as [level, sets, best]
 * ([AnalyticsPayload.skills]) and [score] the overall out of 1000, both null while Skills isn't ready. [completedClasses]:
 * program days completed; [trainedClasses]: classes trained, repeats and own days included; [markedClasses]: days
 * marked done without training. [atLevel] and [atDay]: the first program day not passed yet.
 */
data class InstallSnapshot(
    val versionName: String,
    val versionCode: Int,
    val tier: Tier,
    val debug: Boolean,
    val skills: Map<String, List<Int>>?,
    val score: Int?,
    val switches: String,
    val completedClasses: Int,
    val trainedClasses: Int,
    val markedClasses: Int,
    val atLevel: Int?,
    val atDay: Int?,
)

/** The first-launch placement: each count (null when left empty), the level test picked (0 for none) and the start. */
data class PlacementRecord(
    val pullUps: Int?,
    val pushUps: Int?,
    val squats: Int?,
    val skillTest: Int,
    val startLevel: Int,
    val startDay: Int,
) {
    fun encode(): String = JSONObject(AnalyticsPayload.placement(this).mapValues { it.value ?: JSONObject.NULL }).toString()

    companion object {
        fun decode(text: String?): PlacementRecord? = if (text.isNullOrBlank()) null else runCatching {
            val json = JSONObject(text)
            fun count(key: String) = if (json.isNull(key)) null else json.getInt(key)
            PlacementRecord(count("pullUps"), count("pushUps"), count("squats"), json.getInt("skillTest"),
                json.getInt("startLevel"), json.getInt("startDay"))
        }.getOrNull()
    }
}

/** Google's App Set ID and its scope, "developer" (shared by the owner's apps on the phone) or "app". */
data class AppSetId(val id: String, val scope: String)

/**
 * The documents progress analytics writes ([Analytics]), as plain maps: training progress only, under the install's
 * random code. Nothing here is a name, an email, a place or text the athlete typed: classes and movements go by their ids
 * (checked against [ID], an own day is "custom"), and the account's uid only for a signed-in Pro account. Times are
 * epoch millis ([TIME_FIELDS]); the backend writes them as timestamps and adds the server's time. Every field is in
 * [INSTALL_FIELDS] or [SESSION_FIELDS], as firestore.rules allows.
 */
object AnalyticsPayload {
    const val VERSION = 1

    /** Fields sent as epoch millis that the backend writes as Firestore timestamps. */
    val TIME_FIELDS = setOf("firstSeen", "startedAt", "finishedAt")

    /** Removes the field from the install document: the account's uid, once the account is deleted. */
    object Delete

    /** analytics/{installId}; the backend adds lastSeen, the server's time. */
    val INSTALL_FIELDS = setOf(
        "v", "appSetId", "appSetScope", "accountUid", "firstSeen", "versionName", "versionCode", "tier", "debug",
        "shareProgress", "placement", "skills", "score", "switches", "completedClasses", "trainedClasses", "markedClasses",
        "atLevel", "atDay",
    )
    val PLACEMENT_FIELDS = setOf("pullUps", "pushUps", "squats", "skillTest", "startLevel", "startDay")

    /** analytics/{installId}/sessions/{sessionId}; the backend adds uploadedAt, the server's time. */
    val SESSION_FIELDS = setOf(
        "v", "programId", "level", "day", "dayType", "exam", "custom", "repeat", "intensity", "effort", "startedAt",
        "finishedAt", "durationSec", "plannedSets", "doneSets", "leftOutSets", "exercises",
    )
    val EXERCISE_FIELDS = setOf("section", "movementId", "easier", "test", "sets")
    val SET_FIELDS = setOf("planned", "max", "timed", "done", "ticked", "off")

    /** A movement, skill or day type id; anything else (text) is never sent. */
    val ID = Regex("^[a-z0-9_]{1,64}$")
    private val PROGRAM_ID = Regex("^sg:l[0-9]{2}:p[0-9]{4}$")
    private val SWITCHES = Regex("^[a-z0-9_:,]{0,400}$")

    /** More would only be a damaged class; the rules allow 60. */
    private const val MAX_EXERCISES = 60

    /**
     * The install document. [accountUid]: the Google account signed in, null for the anonymous user (the document keeps
     * the uid it had); [forgetAccount] removes it once the account is deleted. [appSet] null (Play services failed)
     * keeps the last one too.
     */
    fun install(
        snapshot: InstallSnapshot,
        firstSeen: Long,
        appSet: AppSetId?,
        accountUid: String?,
        forgetAccount: Boolean,
        placement: PlacementRecord?,
    ): Map<String, Any?> = buildMap {
        put("v", VERSION)
        appSet?.let { put("appSetId", it.id.take(64)); put("appSetScope", it.scope) }
        if (accountUid != null) put("accountUid", accountUid) else if (forgetAccount) put("accountUid", Delete)
        put("firstSeen", firstSeen)
        put("versionName", snapshot.versionName.take(32))
        put("versionCode", snapshot.versionCode)
        put("tier", snapshot.tier.name.lowercase(Locale.ROOT))
        put("debug", snapshot.debug)
        put("shareProgress", true)
        put("placement", placement?.let(::placement))
        snapshot.skills?.let { skills -> put("skills", skills.filterKeys(ID::matches)) }
        snapshot.score?.let { put("score", it.coerceIn(0, 1000)) }
        put("switches", snapshot.switches.takeIf(SWITCHES::matches).orEmpty())
        put("completedClasses", snapshot.completedClasses)
        put("trainedClasses", snapshot.trainedClasses)
        put("markedClasses", snapshot.markedClasses)
        put("atLevel", snapshot.atLevel)
        put("atDay", snapshot.atDay)
    }

    fun placement(record: PlacementRecord): Map<String, Int?> = mapOf(
        "pullUps" to record.pullUps, "pushUps" to record.pushUps, "squats" to record.squats,
        "skillTest" to record.skillTest, "startLevel" to record.startLevel, "startDay" to record.startDay,
    )

    /** Each started skill: its level (0 none to 5 Elite), its ticked sets and its best set in reps or seconds. */
    fun skills(progress: SkillProgress): Map<String, List<Int>> = progress.statuses.filter { it.started }
        .associate { it.skill.id to listOf(it.level.ordinal, it.sets, it.best) }

    /** A finished class's id under its install: when it started, unique on one phone and the same on every retry. */
    fun sessionId(session: WorkoutSession): String = session.startedAtEpochMillis.toString()

    /**
     * A finished class with every set: [flow] is the class as it ran and [active] the workout just before it finished,
     * with its ticks, easier options and logged reps or seconds. [leftOut]: the set keys the skill switches left out,
     * which don't count as planned. [movementOf] names the movement an exercise trains.
     */
    fun session(
        session: WorkoutSession,
        program: TrainingProgram,
        flow: WorkoutFlow,
        active: ActiveWorkout?,
        leftOut: Set<String>,
        movementOf: (TrainingExercise) -> String? = TrainingExercise::movementId,
    ): Map<String, Any?> {
        val ticked = active?.completedSetIds.orEmpty()
        val easier = active?.easierExercises.orEmpty()
        val done = active?.done.orEmpty()
        val custom = CustomDays.isCustom(session.programId)
        val counted = flow.steps.filter { it.key !in leftOut }
        return mapOf(
            "v" to VERSION,
            "programId" to if (custom) "custom" else session.programId.takeIf(PROGRAM_ID::matches) ?: "unknown",
            "level" to program.levelNumber,
            "day" to program.number,
            "dayType" to program.dayType?.takeIf(ID::matches),
            "exam" to program.isExam,
            "custom" to custom,
            "repeat" to session.repeat,
            "intensity" to session.intensity.name.lowercase(Locale.ROOT),
            "effort" to session.effortRating,
            "startedAt" to session.startedAtEpochMillis,
            "finishedAt" to session.completedAtEpochMillis,
            "durationSec" to session.durationSeconds,
            "plannedSets" to counted.size,
            "doneSets" to counted.count { it.key in ticked },
            "leftOutSets" to flow.size - counted.size,
            "exercises" to flow.steps.groupBy { it.exerciseKey }.values.take(MAX_EXERCISES).map { steps ->
                val first = steps.first()
                mapOf(
                    "section" to first.section,
                    "movementId" to movementOf(first.exercise)?.takeIf(ID::matches),
                    "easier" to first.exercise.easier?.movementId?.takeIf { first.exerciseKey in easier && ID.matches(it) },
                    "test" to first.exercise.assessment,
                    "sets" to steps.map { step ->
                        mapOf(
                            "planned" to step.set.planned,
                            "max" to step.set.repetitionsMax,
                            "timed" to step.set.timed,
                            "done" to done[step.key],
                            "ticked" to (step.key in ticked),
                            "off" to (step.key in leftOut),
                        )
                    },
                )
            },
        )
    }

    /** A session kept on the phone until it is written. */
    fun encode(document: Map<String, Any?>): String = json(document).toString()

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String): Map<String, Any?>? = runCatching { plain(JSONObject(text)) as Map<String, Any?> }.getOrNull()

    private fun json(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply { value.forEach { (key, item) -> put(key as String, json(item)) } }
        is List<*> -> JSONArray().apply { value.forEach { put(json(it)) } }
        else -> value
    }

    private fun plain(value: Any?): Any? = when {
        value == null || value == JSONObject.NULL -> null
        value is JSONObject -> value.keys().asSequence().toList().associateWith { plain(value.opt(it)) }
        value is JSONArray -> List(value.length()) { plain(value.opt(it)) }
        else -> value
    }
}
