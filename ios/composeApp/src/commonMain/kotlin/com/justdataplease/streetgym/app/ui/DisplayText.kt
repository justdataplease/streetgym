package com.justdataplease.streetgym.app.ui

import androidx.compose.runtime.Composable
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.effort_item
import com.justdataplease.streetgym.app.resources.exercise_number
import com.justdataplease.streetgym.app.resources.focus_details_easier
import com.justdataplease.streetgym.app.resources.focus_details_safety
import com.justdataplease.streetgym.app.resources.level_number
import com.justdataplease.streetgym.app.resources.maximum_repetitions
import com.justdataplease.streetgym.app.resources.minutes_format
import com.justdataplease.streetgym.app.resources.repetitions_format
import com.justdataplease.streetgym.app.resources.rest_format
import com.justdataplease.streetgym.app.resources.seconds_format
import com.justdataplease.streetgym.app.resources.set_number
import com.justdataplease.streetgym.app.resources.workout_number
import com.justdataplease.streetgym.app.resources.workout_sequence_position
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import com.justdataplease.streetgym.ui.ExerciseNotes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/* The program's names and doses as words, the way Android writes them (android-streetgym, ui/DisplayText.kt). */

@Composable
fun displayName(level: TrainingLevel): String {
    val value = level.name.resolve()
    return if (value.isBlank() || value == "Level ${level.number}") stringResource(Res.string.level_number, level.number) else value
}

@Composable
fun displayName(program: TrainingProgram): String {
    val value = program.name.resolve()
    return if (value.isBlank() || value == "Workout ${program.number}") stringResource(Res.string.workout_number, program.number) else value
}

@Composable
fun displayName(exercise: TrainingExercise): String =
    exercise.name.resolve().ifBlank { stringResource(Res.string.exercise_number, exercise.number) }

/** "Workout 12 of 100 • About 35 min • RPE 6"; [position] false leaves out the number where it shows already. */
@Composable
fun programMeta(program: TrainingProgram, position: Boolean = true): String = buildList {
    if (position) add(stringResource(Res.string.workout_sequence_position, program.number))
    program.estimatedMinutes?.let { add(stringResource(Res.string.minutes_format, it)) }
    program.targetRpe?.let { add(stringResource(Res.string.effort_item, it)) }
}.joinToString(" • ")

/** "8 reps • per side • Rest 60 sec": one set as the class prescribes it. */
@Composable
fun setPrescription(set: TrainingSet): String = buildList {
    set.name.resolve().takeIf(String::isNotBlank)?.let(::add)
    val repetitions = set.repetitions
    val seconds = set.durationSeconds
    when {
        set.repetitionsMax -> add(stringResource(Res.string.maximum_repetitions))
        repetitions != null -> add(pluralStringResource(Res.plurals.repetitions_format, repetitions, repetitions))
        seconds != null -> add(stringResource(Res.string.seconds_format, seconds))
    }
    set.description.resolve().takeIf(String::isNotBlank)?.let(::add)
    set.breakSeconds?.takeIf { it > 0 }?.let { add(stringResource(Res.string.rest_format, it)) }
}.joinToString(" • ").ifBlank { stringResource(Res.string.set_number, set.number) }

/** A movement's description, then its safety note and easier option on their own lines. */
@Composable
fun exerciseNotes(exercise: TrainingExercise): String {
    val notes = ExerciseNotes.parse(exercise.description.resolve())
    return listOfNotNull(
        notes.description.takeIf(String::isNotBlank),
        notes.safety.takeIf(String::isNotBlank)?.let { "${stringResource(Res.string.focus_details_safety)}: $it" },
        notes.easier.takeIf(String::isNotBlank)?.let { "${stringResource(Res.string.focus_details_easier)}: $it" },
    ).joinToString("\n")
}
