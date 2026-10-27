package com.justdataplease.streetgym.ui

import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingSet

/** One set of a class in training order, with the progress key both workout views save. */
data class WorkoutStep(
    val section: String,
    val exercise: TrainingExercise,
    val exerciseIndex: Int,
    val set: TrainingSet,
    val setIndex: Int,
    val key: String,
) {
    val setNumber: Int get() = set.number.takeIf { it > 0 } ?: setIndex + 1
    /** The exercise this set belongs to, as the easier-option choice is saved. */
    val exerciseKey: String get() = WorkoutFlow.exerciseKey(section, exercise, exerciseIndex)
    val setCount: Int get() = exercise.sets.size
}

/**
 * The ordered sets of one class (warm-up, practice, main, cooldown), free of Android types so the
 * list and focus views walk the same steps and save the same keys.
 */
class WorkoutFlow(val steps: List<WorkoutStep>) {
    val keys: List<String> = steps.map(WorkoutStep::key)
    val size: Int get() = steps.size

    operator fun get(index: Int): WorkoutStep = steps[index]

    fun keys(section: String): List<String> = steps.filter { it.section == section }.map(WorkoutStep::key)

    fun completedCount(completed: Set<String>): Int = keys.count { it in completed }

    /** The focus position: the first set that is neither checked nor skipped. */
    fun firstIncomplete(completed: Set<String>, skipped: Set<String> = emptySet()): Int? =
        steps.indexOfFirst { it.key !in completed && it.key !in skipped }.takeIf { it >= 0 }

    fun next(index: Int): Int? = (index + 1).takeIf { it in steps.indices }

    fun previous(index: Int): Int? = (index - 1).takeIf { it in steps.indices }

    fun isSectionComplete(section: String, completed: Set<String>): Boolean =
        keys(section).let { keys -> keys.isNotEmpty() && keys.all { it in completed } }

    /**
     * What the finished class credits toward skills: ticked sets per movement in the practice and main work. An
     * exercise done as its easier option ([easier] holds [exerciseKey]s) credits the easier movement; readiness checks
     * are answered on their own and credit nothing; [movementOf] names the movement an exercise trains.
     */
    fun credits(completed: Set<String>, easier: Set<String>, movementOf: (TrainingExercise) -> String?): Map<String, Int> {
        val credits = LinkedHashMap<String, Int>()
        for (step in steps) {
            if (step.section !in CREDITED_SECTIONS || step.key !in completed || step.exercise.readinessCheck) continue
            val option = step.exercise.easier?.takeIf { step.exerciseKey in easier }
            val id = option?.movementId ?: movementOf(step.exercise) ?: continue
            credits[id] = (credits[id] ?: 0) + 1
        }
        return credits
    }

    /** Readiness checks with a ticked set: each asks "Did you hit it?" when the class is finished. */
    fun readinessChecks(completed: Set<String>): List<TrainingExercise> = steps
        .filter { it.section in CREDITED_SECTIONS && it.exercise.readinessCheck && it.key in completed }
        .map(WorkoutStep::exercise).distinct()

    companion object {
        /** [sections] are (key, exercises) pairs in class order; empty sections add no steps. */
        fun of(programId: String, sections: List<Pair<String, List<TrainingExercise>>>): WorkoutFlow = WorkoutFlow(
            sections.flatMap { (section, exercises) ->
                exercises.flatMapIndexed { exerciseIndex, exercise ->
                    exercise.sets.mapIndexed { setIndex, set ->
                        WorkoutStep(
                            section, exercise, exerciseIndex, set, setIndex,
                            setKey(programId, section, exercise, exerciseIndex, set, setIndex),
                        )
                    }
                }
            },
        )

        /** The sections whose sets train skills: warm-up and cooldown only prepare and recover. */
        val CREDITED_SECTIONS = setOf("practice", "main")

        fun exerciseKey(section: String, exercise: TrainingExercise, exerciseIndex: Int): String =
            "$section:${exercise.id}-$exerciseIndex"

        fun setKey(
            programId: String,
            section: String,
            exercise: TrainingExercise,
            exerciseIndex: Int,
            set: TrainingSet,
            setIndex: Int,
        ): String = buildString {
            append(programId).append(':').append(section).append(':')
            append(exercise.id).append('-').append(exerciseIndex).append(':')
            append(set.id).append('-').append(setIndex)
        }
    }
}

/** A movement's notes as the parser joins them: "description; Safety: …; Regression: …". */
data class ExerciseNotes(val description: String, val safety: String, val easier: String) {
    companion object {
        private const val SAFETY = "Safety:"
        private const val REGRESSION = "Regression:"

        fun parse(text: String): ExerciseNotes {
            val parts = text.split("; ").map(String::trim).filter(String::isNotBlank)
            fun tagged(tag: String) = parts.filter { it.startsWith(tag) }
                .joinToString(" ") { it.removePrefix(tag).trim() }
            return ExerciseNotes(
                description = parts.filterNot { it.startsWith(SAFETY) || it.startsWith(REGRESSION) }.joinToString("; "),
                safety = tagged(SAFETY),
                easier = tagged(REGRESSION),
            )
        }
    }
}

/** A set's notes split into the side qualifier ("per side") and cues such as the tempo. */
data class SetNotes(val sides: List<String>, val cues: List<String>) {
    companion object {
        private val SIDE_NOTES = setOf("per side")

        fun parse(text: String): SetNotes {
            val (sides, cues) = text.split("; ").map(String::trim).filter(String::isNotBlank)
                .partition { it in SIDE_NOTES }
            return SetNotes(sides, cues)
        }
    }
}
