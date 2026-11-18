package com.justdataplease.streetgym.app.ui

import androidx.compose.runtime.Composable
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.effort_item
import com.justdataplease.streetgym.app.resources.equipment_band_short
import com.justdataplease.streetgym.app.resources.equipment_box_short
import com.justdataplease.streetgym.app.resources.equipment_low_bar_short
import com.justdataplease.streetgym.app.resources.equipment_needs
import com.justdataplease.streetgym.app.resources.equipment_or
import com.justdataplease.streetgym.app.resources.equipment_parallel_bars_short
import com.justdataplease.streetgym.app.resources.equipment_pole_short
import com.justdataplease.streetgym.app.resources.equipment_pullup_bar_short
import com.justdataplease.streetgym.app.resources.equipment_wall_short
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
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentNeeds
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import com.justdataplease.streetgym.ui.ExerciseNotes
import org.jetbrains.compose.resources.StringResource
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

/**
 * A countdown as "01:30", or "1:02:05" from an hour up; [roundUp] counts a started second as whole, so a countdown
 * shows 00:01 until it ends (Android: timer/TimingState.clockText, without String.format).
 */
fun clockText(milliseconds: Long, roundUp: Boolean = false): String {
    val seconds = (milliseconds.coerceAtLeast(0L) + if (roundUp) 999L else 0L) / 1_000L
    fun two(value: Long) = value.toString().padStart(2, '0')
    return if (seconds >= 3_600L) "${seconds / 3_600L}:${two(seconds / 60L % 60L)}:${two(seconds % 60L)}"
    else "${two(seconds / 60L)}:${two(seconds % 60L)}"
}

/** Points with thousands grouped, "12,480" (Android: pointsText, which the app shows in English). */
fun pointsText(points: Int): String {
    val digits = kotlin.math.abs(points.toLong()).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (points < 0) "-$grouped" else grouped
}

/** The equipment's short name in "Needs: pull-up bar" (Android: EquipmentView.equipmentShort). */
private fun shortName(equipment: Equipment): StringResource = when (equipment) {
    Equipment.PULLUP_BAR -> Res.string.equipment_pullup_bar_short
    Equipment.LOW_BAR -> Res.string.equipment_low_bar_short
    Equipment.PARALLEL_BARS -> Res.string.equipment_parallel_bars_short
    Equipment.BOX -> Res.string.equipment_box_short
    Equipment.WALL -> Res.string.equipment_wall_short
    Equipment.POLE -> Res.string.equipment_pole_short
    Equipment.BAND -> Res.string.equipment_band_short
}

/**
 * "Needs: pull-up bar", "Needs: low bar + box" or "Needs: box, low bar or parallel bars" under an exercise; null when any
 * spot will do (Android: equipmentNeeds and equipmentText).
 */
@Composable
fun equipmentNeeds(tags: Collection<String>): String? {
    val needs = EquipmentNeeds.of(tags).takeIf { it.isNotEmpty() } ?: return null
    val text = needs.map { need ->
        val names = need.map { stringResource(shortName(it)) }
        if (names.size == 1) names[0] else stringResource(Res.string.equipment_or, names.dropLast(1).joinToString(", "), names.last())
    }.joinToString(" + ")
    return stringResource(Res.string.equipment_needs, text)
}

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
