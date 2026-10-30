package com.justdataplease.streetgym.ui

import android.content.Context
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import java.util.Locale

fun Context.displayName(level: TrainingLevel, locale: Locale): String {
    LevelNames(this).name(level.number)?.let { return it }
    val value = level.name.resolve(locale)
    return if (value.isBlank() || value == "Level ${level.number}") {
        getString(R.string.level_number, level.number)
    } else {
        value
    }
}

fun Context.displayName(program: TrainingProgram, locale: Locale): String {
    val value = program.name.resolve(locale)
    return if (value.isBlank() || value == "Workout ${program.number}") {
        getString(R.string.workout_number, program.number)
    } else {
        value
    }
}

/** A catalog id such as "static_stretch" or "doorway_or_post" as words. */
fun humanize(id: String): String = id.replace('_', ' ').trim().replaceFirstChar { it.titlecase(Locale.ENGLISH) }

fun Context.displayName(exercise: TrainingExercise, locale: Locale): String =
    exercise.name.resolve(locale).ifBlank { getString(R.string.exercise_number, exercise.number) }

/** "Strict Pull-Up · 10 reps", "Front Lever · 8 s · per side": an exam's target as the class prescribes it. */
fun Context.examTarget(exercise: TrainingExercise, locale: Locale): String {
    val set = exercise.sets.firstOrNull()
    val dose = when {
        set?.repetitions != null -> getString(R.string.repetitions_format, set.repetitions)
        set?.durationSeconds != null -> getString(R.string.focus_seconds, set.durationSeconds)
        else -> null
    }
    val sides = set?.let { SetNotes.parse(it.description.resolve(locale)).sides }.orEmpty()
    return (listOfNotNull(displayName(exercise, locale), dose) + sides).joinToString(" · ")
}

fun Context.programMeta(program: TrainingProgram): String = buildList {
    add(getString(
        R.string.workout_sequence_position,
        program.number,
    ))
    program.estimatedMinutes?.let { add(getString(R.string.minutes_format, it)) }
    program.targetRpe?.let { add(getString(R.string.effort_item, it)) }
}.joinToString(" • ")

fun Context.setPrescription(set: TrainingSet, locale: Locale): String = buildList {
    set.name.resolve(locale).takeIf(String::isNotBlank)?.let(::add)
    when {
        set.repetitionsMax -> add(getString(R.string.maximum_repetitions))
        set.repetitions != null -> add(getString(R.string.repetitions_format, set.repetitions))
        set.durationSeconds != null -> add(getString(R.string.seconds_format, set.durationSeconds))
    }
    set.description.resolve(locale).takeIf(String::isNotBlank)?.let(::add)
    set.breakSeconds?.takeIf { it > 0 }?.let { add(getString(R.string.rest_format, it)) }
}.joinToString(" • ").ifBlank { getString(R.string.set_number, set.number) }
