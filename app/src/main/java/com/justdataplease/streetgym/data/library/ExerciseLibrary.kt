package com.justdataplease.streetgym.data.library

import com.justdataplease.streetgym.data.model.CurriculumCatalog
import com.justdataplease.streetgym.data.model.TrainingExercise
import java.util.Locale

/** One movement the athlete can add to a custom day, with the dose the program first gives it. */
data class LibraryMovement(
    val key: String,
    val name: String,
    val movementId: String?,
    val category: String?,
    val equipment: List<String>,
    val description: String,
    val sample: CustomSet,
    /** How many program days use it: the most used come first. */
    val uses: Int,
)

/** Every distinct exercise of the program, searchable by name, category and equipment. */
class ExerciseLibrary(val movements: List<LibraryMovement>) {
    fun search(query: String): List<LibraryMovement> {
        val words = query.lowercase(Locale.ROOT).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return movements
        return movements.filter { movement ->
            val text = listOfNotNull(movement.name, movement.category, *movement.equipment.toTypedArray())
                .joinToString(" ").replace('_', ' ').lowercase(Locale.ROOT)
            words.all(text::contains)
        }.sortedWith(compareBy<LibraryMovement> { !it.name.lowercase(Locale.ROOT).startsWith(words.first()) }
            .thenByDescending { it.uses })
    }

    companion object {
        fun key(exercise: TrainingExercise): String =
            exercise.movementId ?: exercise.name.resolve().trim().lowercase(Locale.ROOT)

        fun from(catalog: CurriculumCatalog): ExerciseLibrary {
            val first = linkedMapOf<String, TrainingExercise>()
            val uses = mutableMapOf<String, Int>()
            catalog.levels.flatMap { it.programs }.forEach { program ->
                (program.warmup + program.practice + program.exercises + program.cooldown)
                    .filter { it.name.resolve().isNotBlank() }
                    .distinctBy(::key)
                    .forEach { exercise ->
                        val key = key(exercise)
                        if (key !in first) first[key] = exercise
                        uses[key] = (uses[key] ?: 0) + 1
                    }
            }
            return ExerciseLibrary(first.map { (key, exercise) ->
                val set = exercise.sets.firstOrNull()
                LibraryMovement(
                    key = key, name = exercise.name.resolve().trim(), movementId = exercise.movementId,
                    category = exercise.category, equipment = exercise.equipment,
                    description = exercise.description.resolve(),
                    sample = CustomSet(
                        repetitions = set?.repetitions ?: if (set?.durationSeconds == null) 8 else null,
                        durationSeconds = set?.durationSeconds,
                        breakSeconds = set?.breakSeconds ?: 60,
                    ),
                    uses = uses[key] ?: 0,
                )
            }.sortedByDescending { it.uses })
        }
    }
}
