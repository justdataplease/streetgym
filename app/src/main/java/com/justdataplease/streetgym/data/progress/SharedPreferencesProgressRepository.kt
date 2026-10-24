package com.justdataplease.streetgym.data.progress

import android.content.Context
import android.content.SharedPreferences
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

    override fun completedProgramIds(): Set<String> =
        preferences.getStringSet(KEY_COMPLETED_PROGRAM_IDS, emptySet()).orEmpty().toSet()

    override fun isProgramCompleted(programId: String): Boolean =
        programId in completedProgramIds()

    override fun sessionHistory(): List<WorkoutSession> = synchronized(lock) {
        decodeSessions(preferences.getString(KEY_SESSION_HISTORY, null))
            .sortedByDescending(WorkoutSession::completedAtEpochMillis)
    }

    override fun activeWorkout(): ActiveWorkout? = synchronized(lock) {
        decodeActiveWorkout(preferences.getString(KEY_ACTIVE_WORKOUT, null))
    }

    override fun startWorkout(programId: String, startedAtEpochMillis: Long) {
        require(programId.isNotBlank()) { "programId cannot be blank" }
        val workout = ActiveWorkout(programId, startedAtEpochMillis)
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

    override fun finishWorkout(
        completedAtEpochMillis: Long,
        effortRating: Int?,
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
        )
        val completed = completedProgramIds().toMutableSet().apply { add(active.programId) }
        val sessions = decodeSessions(preferences.getString(KEY_SESSION_HISTORY, null)) + session

        preferences.edit()
            .putStringSet(KEY_COMPLETED_PROGRAM_IDS, completed)
            .putString(KEY_SESSION_HISTORY, encodeSessions(sessions).toString())
            .remove(KEY_ACTIVE_WORKOUT)
            .apply()
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
        if (active?.programId in programIds) edit.remove(KEY_ACTIVE_WORKOUT)
        edit.apply()
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

    private fun encodeSessions(sessions: List<WorkoutSession>): JSONArray = JSONArray().apply {
        sessions.forEach { session ->
            put(
                JSONObject()
                    .put("program_id", session.programId)
                    .put("started_at_epoch_millis", session.startedAtEpochMillis)
                    .put("completed_at_epoch_millis", session.completedAtEpochMillis)
                    .put("duration_seconds", session.durationSeconds)
                    .put("manually_completed", session.manuallyCompleted)
                    .put("effort_rating", session.effortRating ?: JSONObject.NULL),
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
    }
}
