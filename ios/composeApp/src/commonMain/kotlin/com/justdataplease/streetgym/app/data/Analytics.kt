package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.ReadyWorkouts
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.ActiveWorkout
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillProgress
import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject
import com.justdataplease.streetgym.ui.WorkoutFlow
import com.justdataplease.streetgym.ui.planned
import com.justdataplease.streetgym.ui.timed
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * Progress analytics, behind "Share my progress" (android-streetgym, analytics/Analytics.kt, AnalyticsPayload.kt and
 * FirestoreAnalytics.kt, line for line): the same documents, fields, ids and limits, so the server reads both phones
 * alike. The documents go through [CloudDocuments]; nothing here is Firebase's own.
 */

/** Who writes: [accountUid] for a signed-in account, null for the anonymous user. */
data class Writer(val accountUid: String?)

/** Where analytics goes: Firestore through [CloudAnalyticsBackend] in the app, a fake in tests. */
interface AnalyticsBackend {
    /** Whoever is signed in, else a new anonymous user; [done] gets null when no one could sign in. */
    fun signIn(done: (Writer?) -> Unit)

    /** One batch: the install document's fields as given (others stay) and [sessions] by id; [done]: the server took it. */
    fun write(installId: String, install: Map<String, Any?>, sessions: Map<String, Map<String, Any?>>, done: (Boolean) -> Unit)

    /** Deletes the install document and the sessions [sessionIds] under it; [done]: the server took it. */
    fun delete(installId: String, sessionIds: Collection<String>, done: (Boolean) -> Unit)
}

/** Android's App Set ID and its scope, "developer" or "app"; on iPhone the identifier for vendor, in its place. */
data class AppSetId(val id: String, val scope: String)

/** Where the install's chain id comes from (Android: PlayAppSet); [fetch] gives null when the phone can't. */
fun interface AppSetSource {
    fun fetch(done: (AppSetId?) -> Unit)

    companion object {
        val None = AppSetSource { it(null) }
    }
}

/**
 * The install's training as the app sees it when it syncs (Android: InstallSnapshot). [tier] the tier the app believes;
 * [debug] a debug build. [skills]: each started skill as [level, sets, best]; [score] is no longer sent (null).
 * [atLevel] and [atDay]: the first program day not passed yet.
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

/** The strength test: each count (null when left empty), the level test picked (0 for none) and the start. */
data class PlacementRecord(
    val pullUps: Int?,
    val pushUps: Int?,
    val squats: Int?,
    val skillTest: Int,
    val startLevel: Int,
    val startDay: Int,
) {
    fun encode(): String = JSONObject().apply {
        AnalyticsPayload.placement(this@PlacementRecord).forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }.toString()

    companion object {
        fun decode(text: String?): PlacementRecord? = if (text.isNullOrBlank()) null else runCatching {
            val json = JSONObject(text)
            fun count(key: String) = if (json.isNull(key)) null else json.getInt(key)
            PlacementRecord(count("pullUps"), count("pushUps"), count("squats"), json.getInt("skillTest"),
                json.getInt("startLevel"), json.getInt("startDay"))
        }.getOrNull()
    }
}

/**
 * The documents progress analytics writes, as plain maps (Android: AnalyticsPayload): training progress only, under
 * the install's random code. Nothing here is a name, an email, a place or text the athlete typed: classes and movements
 * go by their ids, an own day is "custom", and the account's uid only for a signed-in account. Times are epoch millis
 * ([TIME_FIELDS]), written as timestamps; the backend adds the server's time. Every field is one firestore.rules allows.
 */
object AnalyticsPayload {
    const val VERSION = 1

    val TIME_FIELDS = setOf("firstSeen", "startedAt", "finishedAt")

    /** Removes the field from the install document: the account's uid, once the account is deleted. */
    object Delete

    val INSTALL_FIELDS = setOf(
        "v", "appSetId", "appSetScope", "accountUid", "firstSeen", "versionName", "versionCode", "tier", "debug",
        "shareProgress", "placement", "skills", "score", "switches", "completedClasses", "trainedClasses", "markedClasses",
        "atLevel", "atDay",
    )
    val PLACEMENT_FIELDS = setOf("pullUps", "pushUps", "squats", "skillTest", "startLevel", "startDay")
    val SESSION_FIELDS = setOf(
        "v", "programId", "level", "day", "dayType", "exam", "custom", "repeat", "intensity", "effort", "startedAt",
        "finishedAt", "durationSec", "plannedSets", "doneSets", "leftOutSets", "exercises",
    )
    val EXERCISE_FIELDS = setOf("section", "movementId", "easier", "test", "sets")
    val SET_FIELDS = setOf("planned", "max", "timed", "done", "ticked", "off")

    /** A movement, skill or day type id; anything else (text) is never sent. */
    val ID = Regex("^[a-z0-9_]{1,64}$")
    private val PROGRAM_ID = Regex("^sg:l[0-9]{2}:p[0-9]{4}$")
    private val READY_ID = Regex("^ready:[a-z0-9_]{1,40}$")
    private val SWITCHES = Regex("^[a-z0-9_:,]{0,400}$")
    private const val MAX_EXERCISES = 60

    /** The install document (Android: install). [accountUid] null keeps the uid it had; [forgetAccount] removes it. */
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
        put("tier", snapshot.tier.name.lowercase())
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

    /** Each started skill: its level (0 none to 5 Elite), its ticked sets and its best set. */
    fun skills(progress: SkillProgress): Map<String, List<Int>> = progress.statuses.filter { it.started }
        .associate { it.skill.id to listOf(it.level.ordinal, it.sets, it.best) }

    /** A finished class's id under its install: when it started, the same on every retry. */
    fun sessionId(session: WorkoutSession): String = session.startedAtEpochMillis.toString()

    /**
     * A finished class with every set (Android: session): [flow] is the class as it ran, [active] the workout just
     * before it finished, [leftOut] the set keys the switches left out; [movementOf] names an exercise's movement.
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
            "programId" to when {
                custom -> "custom"
                ReadyWorkouts.isReady(session.programId) -> session.programId.takeIf(READY_ID::matches) ?: "unknown"
                else -> session.programId.takeIf(PROGRAM_ID::matches) ?: "unknown"
            },
            "level" to program.levelNumber,
            "day" to program.number,
            "dayType" to program.dayType?.takeIf(ID::matches),
            "exam" to program.isExam,
            "custom" to custom,
            "repeat" to session.repeat,
            "intensity" to session.intensity.name.lowercase(),
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
        value == null || value === JSONObject.NULL -> null
        value is JSONObject -> value.keySet().toList().associateWith { plain(value.opt(it)) }
        value is JSONArray -> List(value.length()) { plain(value.opt(it)) }
        else -> value
    }
}

/**
 * Progress analytics (Android: Analytics): every tier's training progress under this install's random code, made on
 * first use; the identifier for vendor chains the codes of one phone across reinstalls, and a signed-in account's uid
 * chains phones. It runs only while "Share my progress" is on ([sharing]) and the build has Firebase ([backend]).
 * - [sync] writes the install document when the app opens, at most every [SYNC_MILLIS];
 * - [classFinished] keeps a finished class on the phone until a write the server confirmed carries it;
 * - [shareChanged] off stops all of it and deletes what this install wrote, retried until the server confirms.
 * Kept under Android's names ("streetgym_analytics" › install_id, first_seen, pending_sessions…), sets as JSON arrays.
 */
class Analytics(
    private val store: KeyValueStore,
    private val backend: AnalyticsBackend?,
    private val appSet: AppSetSource,
    private val sharing: () -> Boolean,
    private val clock: () -> Long,
) {
    private var running = false
    private var rerun = false
    private var install: InstallSnapshot? = null

    /** This install's random code, made on first use with the time it was made (the install document's firstSeen). */
    @OptIn(ExperimentalUuidApi::class)
    val installId: String
        get() = store.get(KEY_INSTALL_ID) ?: Uuid.random().toString().also {
            store.set(KEY_INSTALL_ID, it)
            store.set(KEY_FIRST_SEEN, clock().toString())
        }

    fun pending(): Set<String> = set(KEY_PENDING)
    fun uploaded(): Set<String> = set(KEY_UPLOADED)

    val deletePending: Boolean get() = flag(KEY_DELETE)

    fun sync(force: Boolean = false, snapshot: () -> InstallSnapshot) {
        if (backend == null) return
        val last = store.get(KEY_LAST_ATTEMPT)?.toLongOrNull() ?: 0L
        val due = sharing() && (force || clock() - last !in 0 until SYNC_MILLIS)
        if (due) install = snapshot()
        if (due || deletePending) pump()
    }

    fun classFinished(sessionId: String, session: Map<String, Any?>, snapshot: () -> InstallSnapshot) {
        if (backend == null || !sharing()) return
        putSet(KEY_PENDING, pending() + sessionId)
        store.set(KEY_SESSION + sessionId, AnalyticsPayload.encode(session))
        install = snapshot()
        pump()
    }

    fun placed(placement: PlacementRecord) {
        if (backend == null) return
        store.set(KEY_PLACEMENT, placement.encode())
    }

    fun shareChanged(on: Boolean) {
        if (backend == null) return
        if (on) return store.set(KEY_LAST_ATTEMPT, null)
        val written = pending() + uploaded()
        listOf(KEY_PENDING, KEY_UPLOADED, KEY_FORGET_ACCOUNT, KEY_LAST_ATTEMPT).forEach { store.set(it, null) }
        written.forEach { store.set(KEY_SESSION + it, null) }
        if (flag(KEY_SHARED)) {
            store.set(KEY_DELETE, "true")
            putSet(KEY_DELETE_IDS, deleteIds() + written)
        }
        install = null
        pump()
    }

    fun accountDeleted() {
        if (backend == null || !flag(KEY_SHARED)) return
        store.set(KEY_FORGET_ACCOUNT, "true")
        store.set(KEY_LAST_ATTEMPT, null)
    }

    private fun deleteIds(): Set<String> = set(KEY_DELETE_IDS)

    private fun placement(): PlacementRecord? = PlacementRecord.decode(store.get(KEY_PLACEMENT))

    private fun pump() {
        val backend = backend ?: return
        if (running) {
            rerun = true
            return
        }
        val deleting = deletePending
        if (!deleting && (!sharing() || install == null)) return
        running = true
        if (!deleting) store.set(KEY_LAST_ATTEMPT, clock().toString())
        backend.signIn { writer ->
            when {
                writer == null -> finish()
                deleting -> backend.delete(installId, deleteIds()) { ok ->
                    if (ok) listOf(KEY_DELETE, KEY_DELETE_IDS, KEY_SHARED).forEach { store.set(it, null) }
                    finish(again = ok)
                }
                else -> write(backend, writer)
            }
        }
    }

    private fun write(backend: AnalyticsBackend, writer: Writer) {
        val snapshot = install ?: return finish()
        val id = installId
        appSet.fetch { appSetId ->
            if (!sharing()) return@fetch finish()
            val ids = pending().sorted().take(MAX_SESSIONS)
            val sessions = ids.mapNotNull { session ->
                store.get(KEY_SESSION + session)?.let(AnalyticsPayload::decode)?.let { session to it }
            }.toMap()
            val document = AnalyticsPayload.install(snapshot, store.get(KEY_FIRST_SEEN)?.toLongOrNull() ?: 0L, appSetId,
                writer.accountUid, flag(KEY_FORGET_ACCOUNT), placement())
            // From here the documents may reach the server, so switching off deletes them.
            store.set(KEY_SHARED, "true")
            backend.write(id, document, sessions) { ok ->
                if (ok && sharing()) {
                    putSet(KEY_PENDING, pending() - ids.toSet())
                    putSet(KEY_UPLOADED, uploaded() + ids)
                    store.set(KEY_FORGET_ACCOUNT, null)
                    ids.forEach { store.set(KEY_SESSION + it, null) }
                    if (install === snapshot) install = null
                }
                finish()
            }
        }
    }

    private fun finish(again: Boolean = false) {
        running = false
        if (rerun || again) {
            rerun = false
            pump()
        }
    }

    private fun flag(key: String): Boolean = store.get(key)?.toBooleanStrictOrNull() ?: false

    private fun set(key: String): Set<String> = runCatching {
        JSONArray(store.get(key) ?: return emptySet()).let { list -> (0 until list.length()).map(list::optString).toSet() }
    }.getOrDefault(emptySet())

    private fun putSet(key: String, values: Set<String>) = store.set(key, if (values.isEmpty()) null else JSONArray(values.sorted()).toString())

    companion object {
        const val PREFIX = "streetgym_analytics."
        /** The install document goes up at most this often when the app opens: a write is what Firestore bills. */
        const val SYNC_MILLIS = 10 * 60_000L
        /** Classes in one write; more wait for the next. */
        const val MAX_SESSIONS = 50
        const val KEY_INSTALL_ID = PREFIX + "install_id"
        const val KEY_FIRST_SEEN = PREFIX + "first_seen"
        const val KEY_LAST_ATTEMPT = PREFIX + "last_attempt"
        const val KEY_PENDING = PREFIX + "pending_sessions"
        const val KEY_UPLOADED = PREFIX + "uploaded_sessions"
        const val KEY_SESSION = PREFIX + "session_"
        const val KEY_PLACEMENT = PREFIX + "placement"
        const val KEY_SHARED = PREFIX + "shared"
        const val KEY_DELETE = PREFIX + "delete_pending"
        const val KEY_DELETE_IDS = PREFIX + "delete_sessions"
        const val KEY_FORGET_ACCOUNT = PREFIX + "forget_account"
    }
}

/**
 * analytics/{installId} and its sessions in Firestore (Android: FirestoreAnalyticsBackend; firestore.rules: write and
 * delete only, nobody reads). The install document's fields are written one by one (merge fields), so one left out
 * keeps its value. The writer is whoever is signed in, else an anonymous user ([anonymousSignIn]).
 */
class CloudAnalyticsBackend(
    private val docs: CloudDocuments,
    private val anonymousSignIn: ((AccountUser?) -> Unit) -> Unit,
) : AnalyticsBackend {
    override fun signIn(done: (Writer?) -> Unit) = anonymousSignIn { user: AccountUser? ->
        done(user?.let { signed -> Writer(if (signed.anonymous) null else signed.uid) })
    }

    override fun write(installId: String, install: Map<String, Any?>, sessions: Map<String, Map<String, Any?>>, done: (Boolean) -> Unit) {
        val root = "$COLLECTION/$installId"
        val fields = values(install) + ("lastSeen" to ServerTime)
        val writes = listOf(CloudWrite.Put(root, fields, mergeFields = fields.keys.toList())) +
            sessions.map { (id, session) -> CloudWrite.Put("$root/$SESSIONS/$id", values(session) + ("uploadedAt" to ServerTime)) }
        docs.write(writes, done)
    }

    override fun delete(installId: String, sessionIds: Collection<String>, done: (Boolean) -> Unit) {
        val root = "$COLLECTION/$installId"
        // A batch holds 500 operations; the install document goes in the last.
        val batches = sessionIds.chunked(BATCH).map { chunk -> chunk.map { CloudWrite.Remove("$root/$SESSIONS/$it") } }
            .ifEmpty { listOf(emptyList()) }
            .let { it.dropLast(1) + listOf(it.last() + CloudWrite.Remove(root)) }
        fun next(index: Int) {
            if (index == batches.size) return done(true)
            docs.write(batches[index]) { ok -> if (ok) next(index + 1) else done(false) }
        }
        next(0)
    }

    /** Epoch millis in [AnalyticsPayload.TIME_FIELDS] become timestamps, [AnalyticsPayload.Delete] a field deletion. */
    private fun values(document: Map<String, Any?>): Map<String, Any?> = document.mapValues { (key, value) ->
        when {
            value === AnalyticsPayload.Delete -> DeleteField
            key in AnalyticsPayload.TIME_FIELDS && value is Number -> CloudTime(value.toLong())
            else -> value
        }
    }

    companion object {
        const val COLLECTION = "analytics"
        const val SESSIONS = "sessions"
        const val BATCH = 450
    }
}
