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

    /** Unsaving a day also takes it out of every list, so saving it again starts in Favorites only. */
    fun setSaved(programId: String, saved: Boolean, at: Long = System.currentTimeMillis()) = update {
        it.copy(saved = it.saved + (programId to SavedMark(saved, at))).let { state ->
            if (saved) state else state.withDayInLists(programId, emptySet(), at)
        }
    }

    /** Saves a program day (when [listIds] is not null, in exactly those lists) or puts a custom day in them. */
    fun saveToLists(dayId: String, listIds: Set<String>?, at: Long = System.currentTimeMillis()) = update { state ->
        val saved = if (CustomDays.isCustom(dayId) || state.saved[dayId]?.saved == true) state
            else state.copy(saved = state.saved + (dayId to SavedMark(true, at)))
        if (listIds == null) saved else saved.withDayInLists(dayId, listIds, at)
    }

    fun saveDay(day: CustomDay) = update { it.copy(days = it.days + (day.id to day)) }

    fun deleteDay(id: String, at: Long = System.currentTimeMillis()) = update { state ->
        val day = state.days[id] ?: return@update state
        state.copy(days = state.days + (id to day.copy(exercises = emptyList(), updatedAt = at, deleted = true)))
            .withDayInLists(id, emptySet(), at)
    }

    fun createList(name: String, at: Long = System.currentTimeMillis()): WorkoutList =
        WorkoutList(WorkoutLists.newId(), WorkoutLists.clean(name), emptyList(), at, at).also { list ->
            update { it.copy(lists = it.lists + (list.id to list)) }
        }

    fun renameList(id: String, name: String, at: Long = System.currentTimeMillis()) = update { state ->
        val list = state.lists[id]?.takeUnless { it.deleted } ?: return@update state
        state.copy(lists = state.lists + (id to list.copy(name = WorkoutLists.clean(name), updatedAt = at)))
    }

    /** The list goes; its days stay in Favorites and any other list. */
    fun deleteList(id: String, at: Long = System.currentTimeMillis()) = update { state ->
        val list = state.lists[id] ?: return@update state
        state.copy(lists = state.lists + (id to list.copy(dayIds = emptyList(), updatedAt = at, deleted = true)))
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
