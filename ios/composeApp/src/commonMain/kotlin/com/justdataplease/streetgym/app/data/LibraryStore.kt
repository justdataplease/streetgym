package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.data.epochMillisNow
import com.justdataplease.streetgym.data.library.CustomDay
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.LibraryCodec
import com.justdataplease.streetgym.data.library.LibraryState
import com.justdataplease.streetgym.data.library.SavedMark
import com.justdataplease.streetgym.data.library.WorkoutList
import com.justdataplease.streetgym.data.library.WorkoutLists
import com.justdataplease.streetgym.json.JSONObject

/**
 * My workouts on this phone (Android: data/library/LibraryRepository.kt): saved program days, the athlete's own
 * workouts and their lists, kept as the engine's [LibraryCodec] writes them, under Android's preferences name and key
 * ("streetgym_library" › "state"), so a backup made on one phone reads on the other. Nothing is removed when a tier
 * ends; the limits only stop new additions. Deleted days and lists stay as tombstones, as on Android.
 *
 * Called from the screen's thread only.
 */
class LibraryStore(private val store: KeyValueStore) {
    /** The last decoded library and its stored text: History and every day lookup ask again with nothing changed. */
    private var decoded: Pair<String?, LibraryState>? = null

    fun state(): LibraryState {
        val raw = store.get(KEY)
        return decoded?.takeIf { it.first == raw }?.second
            ?: LibraryCodec.decode(runCatching { raw?.let(::JSONObject) }.getOrNull()).also { decoded = raw to it }
    }

    fun isSaved(programId: String): Boolean = state().saved[programId]?.saved == true

    fun customDay(id: String): CustomDay? = state().days[id]?.takeUnless { it.deleted }

    /** Unsaving a day also takes it out of every list, so saving it again starts in Favorites only. */
    fun setSaved(programId: String, saved: Boolean, at: Long = epochMillisNow()) = update {
        it.copy(saved = it.saved + (programId to SavedMark(saved, at))).let { state ->
            if (saved) state else state.withDayInLists(programId, emptySet(), at)
        }
    }

    /** Saves a program day (when [listIds] is not null, in exactly those lists) or puts an own workout in them. */
    fun saveToLists(dayId: String, listIds: Set<String>?, at: Long = epochMillisNow()) = update { state ->
        val saved = if (CustomDays.isCustom(dayId) || state.saved[dayId]?.saved == true) state
            else state.copy(saved = state.saved + (dayId to SavedMark(true, at)))
        if (listIds == null) saved else saved.withDayInLists(dayId, listIds, at)
    }

    fun saveDay(day: CustomDay) = update { it.copy(days = it.days + (day.id to day)) }

    fun deleteDay(id: String, at: Long = epochMillisNow()) = update { state ->
        val day = state.days[id] ?: return@update state
        state.copy(days = state.days + (id to day.copy(exercises = emptyList(), updatedAt = at, deleted = true)))
            .withDayInLists(id, emptySet(), at)
    }

    fun createList(name: String, at: Long = epochMillisNow()): WorkoutList =
        WorkoutList(WorkoutLists.newId(), WorkoutLists.clean(name), emptyList(), at, at).also { list ->
            update { it.copy(lists = it.lists + (list.id to list)) }
        }

    fun renameList(id: String, name: String, at: Long = epochMillisNow()) = update { state ->
        val list = state.lists[id]?.takeUnless { it.deleted } ?: return@update state
        state.copy(lists = state.lists + (id to list.copy(name = WorkoutLists.clean(name), updatedAt = at)))
    }

    /** The list goes; its days stay in Favorites and any other list. */
    fun deleteList(id: String, at: Long = epochMillisNow()) = update { state ->
        val list = state.lists[id] ?: return@update state
        state.copy(lists = state.lists + (id to list.copy(dayIds = emptyList(), updatedAt = at, deleted = true)))
    }

    private fun update(change: (LibraryState) -> LibraryState) {
        store.set(KEY, LibraryCodec.encode(change(state())).toString())
    }

    companion object {
        /** Android's preferences name and key, "streetgym_library" and "state", as the other stores write theirs. */
        const val KEY = "streetgym_library.state"
    }
}
