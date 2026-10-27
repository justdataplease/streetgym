package com.justdataplease.streetgym.data.progress

import android.content.Context
import android.content.SharedPreferences
import com.justdataplease.streetgym.data.library.CustomDays
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesProgressRepository(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
) : ProgressRepository {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()
    // The last decoded history and the saved text it came from: Progress and the score check ask for it often.
    private var history: Pair<String?, List<WorkoutSession>>? = null

    override fun completedProgramIds(): Set<String> =
        preferences.getStringSet(KEY_COMPLETED_PROGRAM_IDS, emptySet()).orEmpty().toSet()

    override fun isProgramCompleted(programId: String): Boolean =
        programId in completedProgramIds()

    override fun sessionHistory(): List<WorkoutSession> = synchronized(lock) {
        val raw = preferences.getString(KEY_SESSION_HISTORY, null)
        history?.takeIf { it.first == raw }?.second
            ?: decodeSessions(raw).sortedByDescending(WorkoutSession::completedAtEpochMillis).also { history = raw to it }
    }

    override fun activeWorkout(): ActiveWorkout? = synchronized(lock) {
        decodeActiveWorkout(preferences.getString(KEY_ACTIVE_WORKOUT, null))
    }

    override fun startWorkout(programId: String, startedAtEpochMillis: Long, repeat: Boolean) {
        require(programId.isNotBlank()) { "programId cannot be blank" }
        require(!repeat || isProgramCompleted(programId)) { "only a completed day can be repeated" }
        val workout = ActiveWorkout(programId, startedAtEpochMillis, repeat = repeat)
        preferences.edit().putString(KEY_ACTIVE_WORKOUT, encode(workout).toString()).apply()
    }

    override fun setCompleted(setId: String, completed: Boolean) =
        setCompleted(listOf(setId), completed)

    /** Update a whole section in one saved snapshot. Other sections stay untouched. */
    fun setCompleted(setIds: Collection<String>, completed: Boolean) = synchronized(lock) {
        require(setIds.all(String::isNotBlank)) { "setId cannot be blank" }
        val active = activeWorkout() ?: return@synchronized
        val updated = active.completedSetIds.toMutableSet().apply {
            if (completed) addAll(setIds) else removeAll(setIds.toSet())
        }
        preferences.edit()
            .putString(KEY_ACTIVE_WORKOUT, encode(active.copy(completedSetIds = updated)).toString())
            .apply()
    }

    override fun setEasier(exerciseKey: String, easier: Boolean) = synchronized(lock) {
        require(exerciseKey.isNotBlank()) { "exerciseKey cannot be blank" }
        val active = activeWorkout() ?: return@synchronized
        val updated = if (easier) active.easierExercises + exerciseKey else active.easierExercises - exerciseKey
        preferences.edit()
            .putString(KEY_ACTIVE_WORKOUT, encode(active.copy(easierExercises = updated)).toString())
            .apply()
    }

    override fun finishWorkout(
        completedAtEpochMillis: Long,
        effortRating: Int?,
        credits: Map<String, Int>?,
    ): WorkoutSession? = synchronized(lock) {
        require(effortRating == null || effortRating in 1..10) {
            "effortRating must be between 1 and 10"
        }
        val active = activeWorkout() ?: return@synchronized null
        val completedAt = completedAtEpochMillis.coerceAtLeast(active.startedAtEpochMillis)
        val session = WorkoutSession(
            programId = active.programId,
            startedAtEpochMillis = active.startedAtEpochMillis,
            completedAtEpochMillis = completedAt,
            durationSeconds = (completedAt - active.startedAtEpochMillis) / 1_000L,
            effortRating = effortRating,
            repeat = active.repeat,
            credits = credits?.filterValues { it > 0 },
        )
        val sessions = decodeSessions(preferences.getString(KEY_SESSION_HISTORY, null)) + session
        val edit = preferences.edit()
            .putString(KEY_SESSION_HISTORY, encodeSessions(sessions).toString())
            .remove(KEY_ACTIVE_WORKOUT)
        // A repeat or a custom day only adds its history entry: completed days and the level replay stay exactly as
        // they were.
        if (!active.repeat && !CustomDays.isCustom(active.programId)) {
            edit.putStringSet(KEY_COMPLETED_PROGRAM_IDS, completedProgramIds() + active.programId)
                .putRepeated(listOf(active.programId))
        }
        edit.apply()
        session
    }

    /** Explicit bulk completion: no invented effort or workout duration. */
    fun completePrograms(programIds: Collection<String>, at: Long = System.currentTimeMillis()) = synchronized(lock) {
        require(programIds.all(String::isNotBlank))
        val completed = completedProgramIds().toMutableSet()
        val newIds = programIds.filter { it !in completed }.distinct()
        val active = activeWorkout()
        val sessions = sessionHistory() + newIds.map { id ->
            WorkoutSession(id, at, at, 0L, manuallyCompleted = true)
        }
        completed.addAll(newIds)
        val edit = preferences.edit().putStringSet(KEY_COMPLETED_PROGRAM_IDS, completed)
            .putString(KEY_SESSION_HISTORY, encodeSessions(sessions).toString())
            .putRepeated(programIds)
        // A repeat in progress is of a day already completed, so completing days leaves it running.
        if (active != null && !active.repeat && active.programId in programIds) edit.remove(KEY_ACTIVE_WORKOUT)
        edit.apply()
    }

    /** The level the athlete went back to, or null. Completed days and history are never touched by a replay. */
    fun levelReplay(): LevelReplay? = synchronized(lock) {
        val level = preferences.getInt(KEY_REPLAY_LEVEL, 0)
        if (level <= 0) null
        else LevelReplay(level, preferences.getStringSet(KEY_REPLAY_REPEATED_IDS, emptySet()).orEmpty().toSet(),
            preferences.getString(KEY_REPLAY_FROM, null))
    }

    /** Goes back to [levelNumber]'s first day, or to [fromProgramId] in it (an exam's block). */
    fun startLevelReplay(levelNumber: Int, fromProgramId: String? = null) = synchronized(lock) {
        require(levelNumber > 0)
        preferences.edit().putInt(KEY_REPLAY_LEVEL, levelNumber).remove(KEY_REPLAY_REPEATED_IDS)
            .putString(KEY_REPLAY_FROM, fromProgramId).apply()
    }

    fun endLevelReplay() = synchronized(lock) {
        preferences.edit().remove(KEY_REPLAY_LEVEL).remove(KEY_REPLAY_REPEATED_IDS).remove(KEY_REPLAY_FROM).apply()
    }

    /** During a replay, also records [programIds] as repeated, so Today moves on. */
    private fun SharedPreferences.Editor.putRepeated(programIds: Collection<String>): SharedPreferences.Editor {
        val replay = levelReplay() ?: return this
        return putStringSet(KEY_REPLAY_REPEATED_IDS, replay.repeatedIds + programIds)
    }

    override fun skillTests(): SkillTestLog = synchronized(lock) { SkillTestLog.decode(preferences.getString(KEY_SKILL_TESTS, null)) }

    override fun recordSkillTest(skillId: String, passed: Boolean, at: Long) = synchronized(lock) {
        require(skillId.isNotBlank()) { "skillId cannot be blank" }
        preferences.edit().putString(KEY_SKILL_TESTS, skillTests().record(skillId, passed, at).encode()).apply()
    }

    override fun examLog(): ExamLog = synchronized(lock) { ExamLog.decode(preferences.getString(KEY_EXAMS, null)) }

    override fun recordExam(result: ExamResult) = synchronized(lock) {
        preferences.edit().putString(KEY_EXAMS, examLog().record(result).encode()).apply()
    }

    /** Pro: merges the cloud copy into this device ([ProgressMerge]) and returns the merged copy to upload. */
    fun mergeCloud(completed: Collection<String>, sessionsJson: String?, testsJson: String?, examsJson: String? = null): CloudProgress =
        synchronized(lock) {
            val mergedCompleted = ProgressMerge.completed(completedProgramIds(), completed)
            val merged = encodeSessions(ProgressMerge.sessions(
                decodeSessions(preferences.getString(KEY_SESSION_HISTORY, null)), decodeSessions(sessionsJson))).toString()
            val tests = skillTests().merge(SkillTestLog.decode(testsJson)).encode()
            val exams = examLog().merge(ExamLog.decode(examsJson)).encode()
            preferences.edit().putStringSet(KEY_COMPLETED_PROGRAM_IDS, mergedCompleted).putString(KEY_SESSION_HISTORY, merged)
                .putString(KEY_SKILL_TESTS, tests).putString(KEY_EXAMS, exams).apply()
            CloudProgress(mergedCompleted, merged, tests, exams)
        }

    override fun abandonWorkout() {
        preferences.edit().remove(KEY_ACTIVE_WORKOUT).apply()
    }

    override fun markProgramIncomplete(programId: String) = synchronized(lock) {
        val completed = completedProgramIds().toMutableSet().apply { remove(programId) }
        val sessions = decodeSessions(preferences.getString(KEY_SESSION_HISTORY, null))
            .filterNot { it.programId == programId }
        preferences.edit()
            .putStringSet(KEY_COMPLETED_PROGRAM_IDS, completed)
            .putString(KEY_SESSION_HISTORY, encodeSessions(sessions).toString())
            .apply()
    }

    private fun decodeActiveWorkout(raw: String?): ActiveWorkout? = runCatching {
        if (raw.isNullOrBlank()) return@runCatching null
        val json = JSONObject(raw)
        ActiveWorkout(
            programId = json.getString("program_id"),
            startedAtEpochMillis = json.getLong("started_at_epoch_millis"),
            completedSetIds = json.optJSONArray("completed_set_ids").toStringSet(),
            // Saved before repeats existed: a normal workout.
            repeat = json.optBoolean("repeat", false),
            easierExercises = json.optJSONArray("easier_exercises").toStringSet(),
        )
    }.getOrNull()

    private fun decodeSessions(raw: String?): List<WorkoutSession> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                add(
                    WorkoutSession(
                        programId = json.getString("program_id"),
                        startedAtEpochMillis = json.getLong("started_at_epoch_millis"),
                        completedAtEpochMillis = json.getLong("completed_at_epoch_millis"),
                        durationSeconds = json.optLong("duration_seconds", 0L),
                        manuallyCompleted = json.optBoolean("manually_completed", false),
                        repeat = json.optBoolean("repeat", false),
                        credits = json.optJSONObject("credits")?.let { credits ->
                            credits.keys().asSequence().associateWith { credits.optInt(it) }.filterValues { it > 0 }
                        },
                        effortRating = if (json.isNull("effort_rating")) {
                            null
                        } else {
                            json.optInt("effort_rating").takeIf { it in 1..10 }
                        },
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encode(active: ActiveWorkout): JSONObject = JSONObject()
        .put("program_id", active.programId)
        .put("started_at_epoch_millis", active.startedAtEpochMillis)
        .put("completed_set_ids", JSONArray(active.completedSetIds.sorted()))
        .put("repeat", active.repeat)
        .put("easier_exercises", JSONArray(active.easierExercises.sorted()))

    private fun encodeSessions(sessions: List<WorkoutSession>): JSONArray = JSONArray().apply {
        sessions.forEach { session ->
            put(
                JSONObject()
                    .put("program_id", session.programId)
                    .put("started_at_epoch_millis", session.startedAtEpochMillis)
                    .put("completed_at_epoch_millis", session.completedAtEpochMillis)
                    .put("duration_seconds", session.durationSeconds)
                    .put("manually_completed", session.manuallyCompleted)
                    .put("repeat", session.repeat)
                    .put("effort_rating", session.effortRating ?: JSONObject.NULL)
                    .apply { session.credits?.let { put("credits", JSONObject(it.toSortedMap())) } },
            )
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "streetgym_progress"
        private const val KEY_COMPLETED_PROGRAM_IDS = "completed_program_ids"
        private const val KEY_SESSION_HISTORY = "session_history"
        private const val KEY_ACTIVE_WORKOUT = "active_workout"
        private const val KEY_REPLAY_LEVEL = "replay_level"
        private const val KEY_REPLAY_REPEATED_IDS = "replay_repeated_ids"
        private const val KEY_SKILL_TESTS = "skill_tests"
        private const val KEY_EXAMS = "exam_results"
        private const val KEY_REPLAY_FROM = "replay_from_program_id"
    }
}
