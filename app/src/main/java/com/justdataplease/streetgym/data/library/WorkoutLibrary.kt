package com.justdataplease.streetgym.data.library

import com.justdataplease.streetgym.data.model.ContentOrigin
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

/** One set of a custom exercise: repetitions or seconds, then rest. */
data class CustomSet(val repetitions: Int? = null, val durationSeconds: Int? = null, val breakSeconds: Int = 60)

data class CustomExercise(
    val movementKey: String,
    val name: String,
    val movementId: String? = null,
    val category: String? = null,
    val equipment: List<String> = emptyList(),
    val sets: List<CustomSet>,
)

/** A day the athlete built. A deleted day is kept as a tombstone so a sync can't bring it back. */
data class CustomDay(
    val id: String,
    val name: String,
    val exercises: List<CustomExercise>,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** A program day saved with the heart; unsaving keeps the mark with saved = false for the same reason. */
data class SavedMark(val saved: Boolean, val at: Long)

/**
 * One of the athlete's own lists (Plus and Pro): saved and custom days under a name. Favorites, the default list, is
 * everything in My workouts, so it isn't stored. A deleted list is kept as a tombstone, like a day.
 */
data class WorkoutList(
    val id: String,
    val name: String,
    val dayIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Everything in My workouts: saved program days, custom days and the athlete's lists, by id. */
data class LibraryState(
    val saved: Map<String, SavedMark> = emptyMap(),
    val days: Map<String, CustomDay> = emptyMap(),
    val lists: Map<String, WorkoutList> = emptyMap(),
) {
    val savedIds: List<String> get() = saved.filterValues { it.saved }.entries.sortedByDescending { it.value.at }.map { it.key }
    val customDays: List<CustomDay> get() = days.values.filterNot { it.deleted }.sortedByDescending { it.updatedAt }
    /** Saved and custom days share the tier's limit; lists hold the same days, so they don't count. */
    val used: Int get() = savedIds.size + customDays.size
    /** The lists in the order they were made. */
    val activeLists: List<WorkoutList> get() = lists.values.filterNot { it.deleted }.sortedWith(compareBy({ it.createdAt }, { it.id }))

    /** A saved day or a custom day that still exists: what a list may hold. */
    fun holds(dayId: String): Boolean = saved[dayId]?.saved == true || days[dayId]?.deleted == false

    /** A list's days, newest first, without any that left My workouts. */
    fun daysIn(list: WorkoutList): List<String> = list.dayIds.filter(::holds).reversed()

    fun listIdsWith(dayId: String): Set<String> = activeLists.filter { dayId in it.dayIds }.mapTo(mutableSetOf()) { it.id }

    /** [dayId] in exactly the lists [listIds]; only the lists that change get a new time. */
    fun withDayInLists(dayId: String, listIds: Set<String>, at: Long): LibraryState = copy(lists = lists.mapValues { (id, list) ->
        val inList = dayId in list.dayIds
        when {
            list.deleted || inList == (id in listIds) -> list
            inList -> list.copy(dayIds = list.dayIds - dayId, updatedAt = at)
            else -> list.copy(dayIds = list.dayIds + dayId, updatedAt = at)
        }
    })

    /** Two devices' libraries: the newer change of each saved mark, each day and each list wins. */
    fun merge(other: LibraryState): LibraryState = LibraryState(
        saved = (saved.keys + other.saved.keys).associateWith { id ->
            listOfNotNull(saved[id], other.saved[id]).maxBy { it.at }
        },
        days = (days.keys + other.days.keys).associateWith { id ->
            listOfNotNull(days[id], other.days[id]).maxBy { it.updatedAt }
        },
        lists = (lists.keys + other.lists.keys).associateWith { id ->
            listOfNotNull(lists[id], other.lists[id]).maxBy { it.updatedAt }
        },
    )
}

/** List names and ids. Favorites is the default list, so no other list may take its name. */
object WorkoutLists {
    const val PREFIX = "list:"
    const val MAX_NAME_LENGTH = 30
    fun newId(): String = PREFIX + java.util.UUID.randomUUID()

    enum class NameProblem { Blank, TooLong, Taken }

    fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    /** Why [name] can't be used, or null; [except] is the list being renamed. */
    fun nameProblem(name: String, state: LibraryState, defaultName: String, except: String? = null): NameProblem? {
        val cleaned = clean(name)
        return when {
            cleaned.isEmpty() -> NameProblem.Blank
            cleaned.length > MAX_NAME_LENGTH -> NameProblem.TooLong
            cleaned.equals(defaultName, ignoreCase = true) ||
                state.activeLists.any { it.id != except && it.name.equals(cleaned, ignoreCase = true) } -> NameProblem.Taken
            else -> null
        }
    }
}

object CustomDays {
    const val PREFIX = "custom:"
    fun isCustom(programId: String): Boolean = programId.startsWith(PREFIX)
    fun newId(): String = PREFIX + java.util.UUID.randomUUID()

    /** Work and rest, rounded up to whole minutes: about 3 s a repetition. */
    fun estimatedMinutes(day: CustomDay): Int = day.exercises.sumOf { exercise ->
        exercise.sets.sumOf { (it.durationSeconds ?: ((it.repetitions ?: 0) * 3)) + it.breakSeconds }
    }.let { ceil(it / 60.0).toInt().coerceAtLeast(1) }

    /** A custom day as a program, so the list and focus workouts run it like any other. */
    fun toProgram(day: CustomDay): TrainingProgram = TrainingProgram(
        id = day.id, number = 0, levelNumber = 0, name = LocalizedText(day.name), description = LocalizedText.EMPTY,
        origin = ContentOrigin.UNKNOWN, hasDetails = true, proPlaceholder = false, week = null, day = null,
        estimatedMinutes = estimatedMinutes(day),
        exercises = day.exercises.mapIndexed { index, exercise ->
            TrainingExercise(
                id = "${day.id}-e$index", movementId = exercise.movementId, number = index + 1,
                name = LocalizedText(exercise.name), description = LocalizedText.EMPTY,
                sets = exercise.sets.mapIndexed { setIndex, set ->
                    TrainingSet("${day.id}-e$index-s$setIndex", setIndex + 1, LocalizedText.EMPTY, LocalizedText.EMPTY,
                        set.repetitions, false, set.durationSeconds, set.breakSeconds)
                },
                category = exercise.category, equipment = exercise.equipment,
            )
        },
    )
}

/** The library as JSON, for preferences and the Pro cloud copy. */
object LibraryCodec {
    fun encode(state: LibraryState): JSONObject = JSONObject()
        .put("saved", JSONObject().apply { state.saved.forEach { (id, mark) -> put(id, JSONObject().put("saved", mark.saved).put("at", mark.at)) } })
        .put("days", JSONObject().apply { state.days.forEach { (id, day) -> put(id, encodeDay(day)) } })
        .put("lists", JSONObject().apply {
            state.lists.forEach { (id, list) ->
                put(id, JSONObject().put("name", list.name).put("days", JSONArray(list.dayIds))
                    .put("created_at", list.createdAt).put("updated_at", list.updatedAt).put("deleted", list.deleted))
            }
        })

    fun decode(json: JSONObject?): LibraryState {
        if (json == null) return LibraryState()
        val saved = json.optJSONObject("saved")?.let { marks ->
            marks.keys().asSequence().mapNotNull { id ->
                marks.optJSONObject(id)?.let { id to SavedMark(it.optBoolean("saved"), it.optLong("at")) }
            }.toMap()
        }.orEmpty()
        val days = json.optJSONObject("days")?.let { all ->
            all.keys().asSequence().mapNotNull { id -> all.optJSONObject(id)?.let(::decodeDay)?.let { id to it } }.toMap()
        }.orEmpty()
        // Libraries from before lists have none.
        val lists = json.optJSONObject("lists")?.let { all ->
            all.keys().asSequence().mapNotNull { id ->
                all.optJSONObject(id)?.let { list ->
                    val dayIds = list.optJSONArray("days") ?: JSONArray()
                    id to WorkoutList(id, list.optString("name"), (0 until dayIds.length()).map { dayIds.optString(it) }.distinct(),
                        list.optLong("created_at"), list.optLong("updated_at"), list.optBoolean("deleted"))
                }
            }.toMap()
        }.orEmpty()
        return LibraryState(saved, days, lists)
    }

    fun encodeDay(day: CustomDay): JSONObject = JSONObject()
        .put("id", day.id).put("name", day.name).put("updated_at", day.updatedAt).put("deleted", day.deleted)
        .put("exercises", JSONArray().apply {
            day.exercises.forEach { exercise ->
                put(JSONObject().put("key", exercise.movementKey).put("name", exercise.name)
                    .put("movement_id", exercise.movementId ?: JSONObject.NULL)
                    .put("category", exercise.category ?: JSONObject.NULL)
                    .put("equipment", JSONArray(exercise.equipment))
                    .put("sets", JSONArray().apply {
                        exercise.sets.forEach { set ->
                            put(JSONObject().put("reps", set.repetitions ?: JSONObject.NULL)
                                .put("seconds", set.durationSeconds ?: JSONObject.NULL).put("rest", set.breakSeconds))
                        }
                    }))
            }
        })

    fun decodeDay(json: JSONObject): CustomDay? = runCatching {
        val exercises = json.optJSONArray("exercises") ?: JSONArray()
        CustomDay(
            id = json.getString("id"),
            name = json.optString("name"),
            updatedAt = json.optLong("updated_at"),
            deleted = json.optBoolean("deleted"),
            exercises = (0 until exercises.length()).mapNotNull { index ->
                val exercise = exercises.optJSONObject(index) ?: return@mapNotNull null
                val sets = exercise.optJSONArray("sets") ?: JSONArray()
                val equipment = exercise.optJSONArray("equipment") ?: JSONArray()
                CustomExercise(
                    movementKey = exercise.optString("key"),
                    name = exercise.optString("name"),
                    movementId = exercise.optStringOrNull("movement_id"),
                    category = exercise.optStringOrNull("category"),
                    equipment = (0 until equipment.length()).map { equipment.optString(it) },
                    sets = (0 until sets.length()).mapNotNull { setIndex ->
                        sets.optJSONObject(setIndex)?.let {
                            CustomSet(it.optIntOrNull("reps"), it.optIntOrNull("seconds"), it.optInt("rest", 60))
                        }
                    },
                )
            },
        )
    }.getOrNull()

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
}
