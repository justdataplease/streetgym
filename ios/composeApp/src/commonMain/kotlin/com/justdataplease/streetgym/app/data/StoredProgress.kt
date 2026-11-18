package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.data.progress.ActiveWorkout
import com.justdataplease.streetgym.data.progress.ExamLog
import com.justdataplease.streetgym.data.progress.ExamResult
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.ProgressRepository
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject

/** Where the phone keeps small saved texts: UserDefaults on iPhone, memory in previews and tests. */
interface KeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

/** Kept only while the app runs. */
class MemoryStore : KeyValueStore {
    private val values = HashMap<String, String>()
    override fun get(key: String): String? = values[key]
    override fun set(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
}

/**
 * The athlete's progress on this phone: completed days, the history of finished classes, the class in progress and
 * the exam results. Sessions, the active workout and exams are written as the Android app writes them (the same JSON
 * names, SharedPreferencesProgressRepository), so a backup made on one phone reads on the other. Completed days, a
 * string set on Android, are a JSON array here.
 *
 * Called from the screen's thread only.
 */
class StoredProgress(private val store: KeyValueStore) : ProgressRepository {
    // The last decoded history and the saved text it came from: Today and Progress ask for it often.
    private var history: Pair<String?, List<WorkoutSession>>? = null

    override fun completedProgramIds(): Set<String> = decodeIds(store.get(KEY_COMPLETED))

    override fun isProgramCompleted(programId: String): Boolean = programId in completedProgramIds()

    override fun sessionHistory(): List<WorkoutSession> {
        val raw = store.get(KEY_HISTORY)
        return history?.takeIf { it.first == raw }?.second
            ?: decodeSessions(raw).sortedByDescending(WorkoutSession::completedAtEpochMillis).also { history = raw to it }
    }

    override fun activeWorkout(): ActiveWorkout? = decodeActive(store.get(KEY_ACTIVE))

    override fun startWorkout(programId: String, startedAtEpochMillis: Long, repeat: Boolean, intensity: Intensity) {
        require(programId.isNotBlank()) { "programId cannot be blank" }
        require(!repeat || isProgramCompleted(programId)) { "only a completed day can be repeated" }
        save(ActiveWorkout(programId, startedAtEpochMillis, repeat = repeat, intensity = intensity))
    }

    override fun setCompleted(setId: String, completed: Boolean) = setCompleted(listOf(setId), completed, at = null)

    /** Ticks or unticks several sets in one save. Each newly ticked set keeps the time [at]; unticking forgets it. */
    fun setCompleted(setIds: Collection<String>, completed: Boolean, at: Long?) {
        require(setIds.all(String::isNotBlank)) { "setId cannot be blank" }
        val active = activeWorkout() ?: return
        val ticked = if (completed) active.completedSetIds + setIds else active.completedSetIds - setIds.toSet()
        val times = when {
            !completed -> active.tickedAt - setIds.toSet()
            at != null -> active.tickedAt + setIds.filter { it !in active.completedSetIds }.associateWith { at }
            else -> active.tickedAt
        }
        save(active.copy(completedSetIds = ticked, tickedAt = times))
    }

    override fun setEasier(exerciseKey: String, easier: Boolean) {
        require(exerciseKey.isNotBlank()) { "exerciseKey cannot be blank" }
        val active = activeWorkout() ?: return
        save(active.copy(easierExercises = if (easier) active.easierExercises + exerciseKey else active.easierExercises - exerciseKey))
    }

    override fun setIntensity(intensity: Intensity) {
        save((activeWorkout() ?: return).copy(intensity = intensity))
    }

    override fun setDone(setId: String, amount: Int?) {
        require(setId.isNotBlank()) { "setId cannot be blank" }
        val active = activeWorkout() ?: return
        save(active.copy(done = if (amount != null && amount >= 0) active.done + (setId to amount) else active.done - setId))
    }

    override fun finishWorkout(
        completedAtEpochMillis: Long,
        effortRating: Int?,
        credits: Map<String, Int>?,
        doses: Map<String, List<Int>>?,
        points: Int?,
    ): WorkoutSession? {
        require(effortRating == null || effortRating in 1..10) { "effortRating must be between 1 and 10" }
        val active = activeWorkout() ?: return null
        val completedAt = completedAtEpochMillis.coerceAtLeast(active.startedAtEpochMillis)
        val session = WorkoutSession(
            programId = active.programId,
            startedAtEpochMillis = active.startedAtEpochMillis,
            completedAtEpochMillis = completedAt,
            durationSeconds = (completedAt - active.startedAtEpochMillis) / 1_000L,
            effortRating = effortRating,
            repeat = active.repeat,
            credits = credits?.filterValues { it > 0 },
            intensity = active.intensity,
            doses = doses?.filterValues { it.isNotEmpty() },
            points = points?.coerceAtLeast(0),
        )
        store.set(KEY_HISTORY, encodeSessions(decodeSessions(store.get(KEY_HISTORY)) + session))
        store.set(KEY_ACTIVE, null)
        // A repeat only adds its history entry: completed days stay exactly as they were.
        if (!active.repeat) store.set(KEY_COMPLETED, encodeIds(completedProgramIds() + active.programId))
        return session
    }

    /**
     * Marks days done without training ("Complete day"): no invented effort or duration, and they count for nothing
     * in points, ranks or the streak.
     */
    fun completePrograms(programIds: Collection<String>, at: Long, placed: Boolean = false) {
        require(programIds.all(String::isNotBlank))
        val completed = completedProgramIds()
        val newIds = programIds.filter { it !in completed }.distinct()
        if (newIds.isEmpty()) return
        val active = activeWorkout()
        store.set(KEY_HISTORY, encodeSessions(decodeSessions(store.get(KEY_HISTORY)) +
            newIds.map { WorkoutSession(it, at, at, 0L, manuallyCompleted = true, placed = placed) }))
        store.set(KEY_COMPLETED, encodeIds(completed + newIds))
        // A repeat in progress is of a day already completed, so completing days leaves it running.
        if (active != null && !active.repeat && active.programId in programIds) store.set(KEY_ACTIVE, null)
    }

    override fun examLog(): ExamLog = ExamLog.decode(store.get(KEY_EXAMS))

    override fun recordExam(result: ExamResult) = store.set(KEY_EXAMS, examLog().record(result).encode())

    override fun abandonWorkout() = store.set(KEY_ACTIVE, null)

    override fun markProgramIncomplete(programId: String) {
        store.set(KEY_COMPLETED, encodeIds(completedProgramIds() - programId))
        store.set(KEY_HISTORY, encodeSessions(decodeSessions(store.get(KEY_HISTORY)).filterNot { it.programId == programId }))
    }

    private fun save(active: ActiveWorkout) = store.set(KEY_ACTIVE, encode(active))

    companion object {
        private const val KEY_COMPLETED = "streetgym_progress.completed_program_ids"
        private const val KEY_HISTORY = "streetgym_progress.session_history"
        private const val KEY_ACTIVE = "streetgym_progress.active_workout"
        private const val KEY_EXAMS = "streetgym_progress.exam_results"

        private fun encodeIds(ids: Set<String>): String = JSONArray(ids.sorted()).toString()

        private fun decodeIds(raw: String?): Set<String> = runCatching {
            if (raw.isNullOrBlank()) emptySet() else JSONArray(raw).strings()
        }.getOrDefault(emptySet())

        private fun JSONArray?.strings(): Set<String> =
            if (this == null) emptySet() else (0 until length()).mapNotNullTo(LinkedHashSet()) { optString(it).takeIf(String::isNotBlank) }

        private fun JSONObject.ints(): Map<String, Int> = keySet().associateWith { optInt(it) }

        /** An object with its keys in order, as Android writes its sorted maps. */
        private fun sorted(values: Map<String, Any>): JSONObject =
            JSONObject().apply { values.entries.sortedBy { it.key }.forEach { put(it.key, it.value) } }

        fun encode(active: ActiveWorkout): String = JSONObject()
            .put("program_id", active.programId)
            .put("started_at_epoch_millis", active.startedAtEpochMillis)
            .put("completed_set_ids", JSONArray(active.completedSetIds.sorted()))
            .put("repeat", active.repeat)
            .put("easier_exercises", JSONArray(active.easierExercises.sorted()))
            .put("intensity", active.intensity.name)
            .put("done", sorted(active.done))
            .put("ticked_at", sorted(active.tickedAt))
            .toString()

        fun decodeActive(raw: String?): ActiveWorkout? = runCatching {
            if (raw.isNullOrBlank()) return@runCatching null
            val json = JSONObject(raw)
            ActiveWorkout(
                programId = json.getString("program_id"),
                startedAtEpochMillis = json.getLong("started_at_epoch_millis"),
                completedSetIds = json.optJSONArray("completed_set_ids").strings(),
                repeat = json.optBoolean("repeat", false),
                easierExercises = json.optJSONArray("easier_exercises").strings(),
                intensity = Intensity.fromName(json.optString("intensity", "")),
                done = json.optJSONObject("done")?.ints().orEmpty(),
                tickedAt = json.optJSONObject("ticked_at")?.let { at -> at.keySet().associateWith { at.optLong(it) } }.orEmpty(),
            )
        }.getOrNull()

        fun encodeSessions(sessions: List<WorkoutSession>): String = JSONArray().apply {
            sessions.forEach { session ->
                put(JSONObject()
                    .put("program_id", session.programId)
                    .put("started_at_epoch_millis", session.startedAtEpochMillis)
                    .put("completed_at_epoch_millis", session.completedAtEpochMillis)
                    .put("duration_seconds", session.durationSeconds)
                    .put("manually_completed", session.manuallyCompleted)
                    .put("repeat", session.repeat)
                    .put("effort_rating", session.effortRating ?: JSONObject.NULL)
                    .apply { session.credits?.let { put("credits", sorted(it)) } }
                    // Written only when set, as on Android, so every older session stays as it was.
                    .apply { if (session.placed) put("placed", true) }
                    .apply { if (session.intensity != Intensity.NORMAL) put("intensity", session.intensity.name) }
                    .apply {
                        session.doses?.let { doses ->
                            put("doses", sorted(doses.mapValues { JSONArray(it.value) }))
                        }
                    }
                    .apply { session.points?.let { put("points", it) } })
            }
        }.toString()

        fun decodeSessions(raw: String?): List<WorkoutSession> = runCatching {
            if (raw.isNullOrBlank()) return@runCatching emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                WorkoutSession(
                    programId = json.getString("program_id"),
                    startedAtEpochMillis = json.getLong("started_at_epoch_millis"),
                    completedAtEpochMillis = json.getLong("completed_at_epoch_millis"),
                    durationSeconds = json.optLong("duration_seconds", 0L),
                    manuallyCompleted = json.optBoolean("manually_completed", false),
                    repeat = json.optBoolean("repeat", false),
                    placed = json.optBoolean("placed", false),
                    intensity = Intensity.fromName(json.optString("intensity", "")),
                    credits = json.optJSONObject("credits")?.ints()?.filterValues { it > 0 },
                    doses = json.optJSONObject("doses")?.let { doses ->
                        doses.keySet().associateWith { id ->
                            doses.optJSONArray(id)?.let { list -> List(list.length()) { list.optInt(it) } }.orEmpty()
                        }
                    },
                    points = if (json.has("points")) json.optInt("points").coerceAtLeast(0) else null,
                    effortRating = if (json.isNull("effort_rating")) null else json.optInt("effort_rating").takeIf { it in 1..10 },
                )
            }
        }.getOrDefault(emptyList())
    }
}
