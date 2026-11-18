package com.justdataplease.streetgym.app.ui

import androidx.compose.runtime.Composable
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.effort_item
import com.justdataplease.streetgym.app.resources.custom_meta
import com.justdataplease.streetgym.app.resources.custom_exercises
import com.justdataplease.streetgym.app.resources.ready_grade_1
import com.justdataplease.streetgym.app.resources.ready_grade_2
import com.justdataplease.streetgym.app.resources.ready_grade_3
import com.justdataplease.streetgym.app.resources.ready_grade_4
import com.justdataplease.streetgym.app.resources.ready_grade_5
import com.justdataplease.streetgym.app.resources.ready_meta
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
import com.justdataplease.streetgym.app.resources.effort_easy
import com.justdataplease.streetgym.app.resources.effort_for_you
import com.justdataplease.streetgym.app.resources.effort_hard
import com.justdataplease.streetgym.app.resources.effort_moderate
import com.justdataplease.streetgym.app.resources.focus_seconds
import com.justdataplease.streetgym.app.resources.tier_plus
import com.justdataplease.streetgym.app.resources.tier_pro
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.effort.Effort
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentNeeds
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import com.justdataplease.streetgym.ui.ExerciseNotes
import com.justdataplease.streetgym.ui.SetNotes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

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
fun equipmentNeeds(tags: Collection<String>): String? = equipmentText(tags)?.let { stringResource(Res.string.equipment_needs, it) }

/** "pull-up bar + box" or "box, low bar or parallel bars"; null when any spot will do (Android: equipmentText). */
@Composable
fun equipmentText(tags: Collection<String>): String? {
    val needs = EquipmentNeeds.of(tags).takeIf { it.isNotEmpty() } ?: return null
    return needs.map { need ->
        val names = need.map { stringResource(shortName(it)) }
        if (names.size == 1) names[0] else stringResource(Res.string.equipment_or, names.dropLast(1).joinToString(", "), names.last())
    }.joinToString(" + ")
}

/** A catalog id such as "static_stretch" as words: "Static stretch" (Android: humanize). */
fun humanize(id: String): String = id.replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }

/** "Your workout · 5 exercises · About 20 min" (Android: customMeta). */
@Composable
fun customMeta(exercises: Int, minutes: Int): String =
    stringResource(Res.string.custom_meta, pluralStringResource(Res.plurals.custom_exercises, exercises, exercises), minutes)

/** "Intermediate · 6 exercises · About 30 min" (Android: readyMeta). */
@Composable
fun readyMeta(program: TrainingProgram): String = stringResource(Res.string.ready_meta,
    readyGradeName(program.grade ?: 2),
    pluralStringResource(Res.plurals.custom_exercises, program.exercises.size, program.exercises.size),
    program.estimatedMinutes ?: 1)

/** A ready workout's grade, Beginner to Elite (Android: readyGradeName). */
@Composable
fun readyGradeName(grade: Int): String = stringResource(when (grade) {
    1 -> Res.string.ready_grade_1
    3 -> Res.string.ready_grade_3
    4 -> Res.string.ready_grade_4
    5 -> Res.string.ready_grade_5
    else -> Res.string.ready_grade_2
})

/** "Easy for you", "Moderate for you" or "Hard for you" (Android: EffortUi.effortText). */
@Composable
fun effortText(effort: Effort): String = stringResource(Res.string.effort_for_you, stringResource(when (effort) {
    Effort.EASY -> Res.string.effort_easy
    Effort.MODERATE -> Res.string.effort_moderate
    Effort.HARD -> Res.string.effort_hard
}))

/** An exam target as the exam's question lists it: "Strict Pull-Up · 5 reps", "L-Sit · 15 s · per side" (Android: examTarget). */
@Composable
fun examTarget(exercise: TrainingExercise): String {
    val set = exercise.sets.firstOrNull()
    val repetitions = set?.repetitions
    val seconds = set?.durationSeconds
    val dose = when {
        repetitions != null -> pluralStringResource(Res.plurals.repetitions_format, repetitions, repetitions)
        seconds != null -> stringResource(Res.string.focus_seconds, seconds)
        else -> null
    }
    val sides = set?.let { SetNotes.parse(it.description.resolve()).sides }.orEmpty()
    return (listOfNotNull(displayName(exercise), dose) + sides).joinToString(" · ")
}

/** "Plus" or "Pro": the tier that opens a level (Android: levelTierName). */
@Composable
fun levelTierName(levelNumber: Int): String =
    stringResource(if (LevelAccess.tierFor(levelNumber) == Tier.PRO) Res.string.tier_pro else Res.string.tier_plus)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * A day as Android's medium date shows it in English, "Nov 18, 2026", in the phone's [zone] (Android:
 * DateFormat.getDateInstance(MEDIUM)).
 */
fun mediumDate(millis: Long, zone: TimeZone): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
    return "${MONTHS[date.month.ordinal]} ${date.day}, ${date.year}"
}

/** An exercise's name outside a screen, for what is spoken (Android: displayName). */
suspend fun exerciseName(exercise: TrainingExercise): String =
    exercise.name.resolve().ifBlank { getString(Res.string.exercise_number, exercise.number) }

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
