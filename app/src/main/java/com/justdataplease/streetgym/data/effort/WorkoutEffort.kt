package com.justdataplease.streetgym.data.effort

import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram

/** How hard a workout is for this athlete: green, orange and red on every class, ready workout and custom day. */
enum class Effort { EASY, MODERATE, HARD }

/**
 * Rates workouts for one athlete (the owner, 2.9.0: "green, orange, red like the skills, on every workout, custom or
 * not"; the three coach reviews gave the inputs). A workout's load is the sum over its working sets (practice and main)
 * of its movement's grade weight (1, 1.5, 2, 3 and 4 for grades 1 to 5; a quarter for a stretch, mobility, warm-up,
 * activation or recovery set, 2.9.1), how close the set is to the athlete's best for a logged skill (under 70% of it 0.7,
 * up to it 1, past it 1.3), and 25% more at 45 s rest or less. It is compared with
 * the typical class of the athlete's level, the median load of that level's training classes scored the same way:
 * over 1.25 times it, or two sets past a best, is Hard; under 0.7 times it is Easy; the rest Moderate. A class of the
 * athlete's own level labelled RPE 8 or more is Hard and an exam there never Easy, as the program meant them for that
 * level. tools/build_curriculum.py (set_load) scores the classes' RPE labels with the same numbers.
 *
 * [grades]: each movement's grade, 1 easy to 5 extreme (the movement library). [bests]: the athlete's best set of each
 * skill, in reps or seconds (SkillStatus.best). [level]: the athlete's level, [levelClasses] its classes.
 */
class WorkoutEffort(
    private val grades: Map<String, Int>,
    private val bests: Map<String, Int>,
    private val level: Int,
    levelClasses: List<TrainingProgram>,
) {
    private val typical: Double = levelClasses.filter(::isTraining).map(::load).sorted().let { loads ->
        if (loads.isEmpty()) DEFAULT_TYPICAL else (loads[(loads.size - 1) / 2] + loads[loads.size / 2]) / 2
    }.coerceAtLeast(1.0)

    /** [onProgram]: a class of the ten levels (its RPE label and exam day speak for its own level). */
    fun rate(program: TrainingProgram, onProgram: Boolean = true): Effort {
        val ratio = load(program) / typical
        val own = onProgram && program.levelNumber == level
        return when {
            ratio > HARD_ABOVE || setsPastBest(program) >= 2 || own && !program.isExam && (program.targetRpe ?: 0) >= 8 -> Effort.HARD
            ratio < EASY_BELOW && !(own && (program.isExam || (program.targetRpe ?: 0) >= 7)) -> Effort.EASY
            else -> Effort.MODERATE
        }
    }

    /** The workout's hardest exercise, its icon: the highest grade, then the most load. */
    fun hardest(program: TrainingProgram): TrainingExercise? = working(program)
        .maxWithOrNull(compareBy<TrainingExercise>({ grade(it) }, { exerciseLoad(it) }))

    fun grade(exercise: TrainingExercise): Int = exercise.movementId?.let(grades::get)?.coerceIn(1, 5) ?: 1

    fun load(program: TrainingProgram): Double = working(program).sumOf(::exerciseLoad)

    private fun exerciseLoad(exercise: TrainingExercise): Double {
        val weight = if (exercise.category in LIGHT_CATEGORIES) LIGHT_WEIGHT else GRADE_WEIGHT[grade(exercise) - 1]
        val best = exercise.movementId?.let(bests::get)?.takeIf { it > 0 }
        return exercise.sets.sumOf { set ->
            val dose = set.repetitions ?: set.durationSeconds
            val close = when {
                best == null || dose == null -> 1.0
                dose < 0.7 * best -> 0.7
                dose <= best -> 1.0
                else -> 1.3
            }
            weight * close * (if ((set.breakSeconds ?: 60) <= SHORT_REST) DENSE else 1.0)
        }
    }

    private fun setsPastBest(program: TrainingProgram): Int = working(program).sumOf { exercise ->
        val best = exercise.movementId?.let(bests::get)?.takeIf { it > 0 } ?: return@sumOf 0
        exercise.sets.count { set -> (set.repetitions ?: set.durationSeconds ?: 0) > best }
    }

    private fun working(program: TrainingProgram): List<TrainingExercise> =
        (program.practice + program.exercises).filterNot { it.readinessCheck }

    companion object {
        private val GRADE_WEIGHT = doubleArrayOf(1.0, 1.5, 2.0, 3.0, 4.0)
        /** A stretching or mobility set is no working set: a whole stretching day read Moderate (2.9.1). */
        private const val LIGHT_WEIGHT = 0.25
        private val LIGHT_CATEGORIES = setOf("dynamic_warmup", "activation", "mobility", "static_stretch", "static_cooldown", "recovery")
        private const val SHORT_REST = 45
        private const val DENSE = 1.25
        private const val HARD_ABOVE = 1.25
        private const val EASY_BELOW = 0.7
        /** About a Level 1 class, for a catalog without levels. */
        private const val DEFAULT_TYPICAL = 15.0
        private val LIGHT = listOf("exam", "light", "deload", "primer", "easy", "reentry")

        /** A full training class: no exam, recovery, lighter or easy day. */
        fun isTraining(program: TrainingProgram): Boolean {
            val type = program.dayType ?: return false
            return LIGHT.none(type::startsWith) && "recovery" !in type
        }
    }
}
