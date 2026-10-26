package com.justdataplease.streetgym.data.library

import android.content.Context
import org.json.JSONObject

/** My workouts on this device. Nothing is removed when a tier ends; the limit only stops new additions. */
class LibraryRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    /** The last decoded library and its stored text: History and every day lookup ask again with nothing changed. */
    private var decoded: Pair<String?, LibraryState>? = null
    /** Runs after every change, so Pro's cloud copy can follow. */
    var onChange: (() -> Unit)? = null

    fun state(): LibraryState = synchronized(lock) {
        val raw = preferences.getString(KEY_STATE, null)
        decoded?.takeIf { it.first == raw }?.second
            ?: LibraryCodec.decode(runCatching { raw?.let(::JSONObject) }.getOrNull()).also { decoded = raw to it }
    }

    fun isSaved(programId: String): Boolean = state().saved[programId]?.saved == true

    fun customDay(id: String): CustomDay? = state().days[id]?.takeUnless { it.deleted }

    fun setSaved(programId: String, saved: Boolean, at: Long = System.currentTimeMillis()) = update {
        it.copy(saved = it.saved + (programId to SavedMark(saved, at)))
    }

    fun saveDay(day: CustomDay) = update { it.copy(days = it.days + (day.id to day)) }

    fun deleteDay(id: String, at: Long = System.currentTimeMillis()) = update { state ->
        val day = state.days[id] ?: return@update state
        state.copy(days = state.days + (id to day.copy(exercises = emptyList(), updatedAt = at, deleted = true)))
    }

    /** The merged library of this device and the cloud copy (Pro); not a change to sync back. */
    fun replace(state: LibraryState) = synchronized(lock) {
        preferences.edit().putString(KEY_STATE, LibraryCodec.encode(state).toString()).apply()
    }

    private fun update(change: (LibraryState) -> LibraryState) {
        synchronized(lock) { preferences.edit().putString(KEY_STATE, LibraryCodec.encode(change(state())).toString()).apply() }
        onChange?.invoke()
    }

    companion object {
        const val PREFERENCES_NAME = "streetgym_library"
        private const val KEY_STATE = "state"
    }
}
