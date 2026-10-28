package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.data.model.CurriculumCatalog
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import kotlin.math.roundToInt

/**
 * How hard the strength work runs: the placement's "How strong do you feel?", changed any time in Settings. Easier and
 * Harder scale the reps and hold times of a class's strength exercises by a quarter. The number of sets never changes,
 * so skill points, which count sets, don't either. Skill work keeps its written dose, and so do the warm-up, practice,
 * cool-down, readiness checks, assessments and every exam day.
 */
enum class Intensity(val factor: Double) {
    EASIER(0.75), NORMAL(1.0), HARDER(1.25);

    fun scale(catalog: CurriculumCatalog): CurriculumCatalog = if (this == NORMAL) catalog else
        catalog.copy(levels = catalog.levels.map { level -> level.copy(programs = level.programs.map(::scale)) })

    fun scale(program: TrainingProgram): TrainingProgram =
        if (this == NORMAL || program.isExam) program else program.copy(exercises = program.exercises.map(::scale))

    private fun scale(exercise: TrainingExercise): TrainingExercise = if (!scales(exercise)) exercise else
        exercise.copy(sets = exercise.sets.map { it.copy(repetitions = it.repetitions?.let(::reps), durationSeconds = it.durationSeconds?.let(::seconds)) })

    /** Whole reps, at least one. */
    internal fun reps(written: Int): Int = (written * factor).roundToInt().coerceAtLeast(1)

    /** Whole seconds, to the nearest 5 from 20 s up. */
    internal fun seconds(written: Int): Int {
        val scaled = written * factor
        return (if (written >= 20) (scaled / 5).roundToInt() * 5 else scaled.roundToInt()).coerceAtLeast(1)
    }

    companion object {
        /** Movement categories that scale: strength and conditioning. The skill categories keep their dose. */
        val STRENGTH = setOf("fundamental", "strength", "strength_regression", "advanced_fundamental", "advanced_strength",
            "conditioning", "power", "mobility_strength")

        fun scales(exercise: TrainingExercise): Boolean =
            exercise.category in STRENGTH && !exercise.readinessCheck && !exercise.assessment

        fun fromName(name: String?): Intensity = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
