package com.justdataplease.streetgym.app.ui

import androidx.compose.runtime.Composable
import com.justdataplease.streetgym.app.data.HistoryCalendar
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.equipment_band
import com.justdataplease.streetgym.app.resources.equipment_band_hint
import com.justdataplease.streetgym.app.resources.equipment_box
import com.justdataplease.streetgym.app.resources.equipment_box_hint
import com.justdataplease.streetgym.app.resources.equipment_low_bar
import com.justdataplease.streetgym.app.resources.equipment_low_bar_hint
import com.justdataplease.streetgym.app.resources.equipment_parallel_bars
import com.justdataplease.streetgym.app.resources.equipment_parallel_bars_hint
import com.justdataplease.streetgym.app.resources.equipment_pole
import com.justdataplease.streetgym.app.resources.equipment_pole_hint
import com.justdataplease.streetgym.app.resources.equipment_pullup_bar
import com.justdataplease.streetgym.app.resources.equipment_pullup_bar_hint
import com.justdataplease.streetgym.app.resources.equipment_wall
import com.justdataplease.streetgym.app.resources.equipment_wall_hint
import com.justdataplease.streetgym.app.resources.skills_difficulty_1
import com.justdataplease.streetgym.app.resources.skills_difficulty_2
import com.justdataplease.streetgym.app.resources.skills_difficulty_3
import com.justdataplease.streetgym.app.resources.skills_difficulty_4
import com.justdataplease.streetgym.app.resources.skills_difficulty_5
import com.justdataplease.streetgym.app.resources.skills_level_0
import com.justdataplease.streetgym.app.resources.skills_level_1
import com.justdataplease.streetgym.app.resources.skills_level_2
import com.justdataplease.streetgym.app.resources.skills_level_3
import com.justdataplease.streetgym.app.resources.skills_level_4
import com.justdataplease.streetgym.app.resources.skills_level_5
import com.justdataplease.streetgym.app.resources.skills_max_reps
import com.justdataplease.streetgym.app.resources.skills_max_reps_side
import com.justdataplease.streetgym.app.resources.skills_max_seconds
import com.justdataplease.streetgym.app.resources.skills_max_seconds_side
import com.justdataplease.streetgym.app.resources.skills_need_for
import com.justdataplease.streetgym.app.resources.skills_sets
import com.justdataplease.streetgym.app.resources.skills_sets_at
import com.justdataplease.streetgym.app.resources.skills_target_reps
import com.justdataplease.streetgym.app.resources.skills_target_reps_side
import com.justdataplease.streetgym.app.resources.skills_target_seconds
import com.justdataplease.streetgym.app.resources.skills_target_seconds_side
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillStandard
import com.justdataplease.streetgym.data.skills.SkillStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/*
 * Progress's words as the Android app writes them in English (android-streetgym, HistoryCalendarView's date formats,
 * HomeActivity.historyRow, SkillsScreen and EquipmentView), without a platform date formatter.
 */

private val MONTH_NAMES = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September",
    "October", "November", "December")

/** Sunday to Saturday, as [HistoryCalendar] numbers them from 1 (Android: DateFormatSymbols' weekdays). */
private val WEEKDAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

/** "November 2026": the month's title (Android: the best "MMMMyyyy" pattern in English). */
fun calendarTitle(month: Int): String = "${monthName(month)} ${HistoryCalendar.year(month)}"

/** "November" (Android: "MMMM"). */
fun monthName(month: Int): String = MONTH_NAMES[HistoryCalendar.monthNumber(month) - 1]

/** "November 5": a day as the calendar speaks it (Android: "MMMMd"). */
fun dayTitle(dayKey: Int): String = "${MONTH_NAMES[dayKey / 100 % 100 - 1]} ${dayKey % 100}"

/** "Wednesday, November 5": the picked day over its classes (Android: "EEEEMMMMd"). */
fun weekdayTitle(dayKey: Int): String =
    "${WEEKDAY_NAMES[HistoryCalendar.weekdayOf(HistoryCalendar.date(dayKey)) - 1]}, ${dayTitle(dayKey)}"

/** "Sun" to "Sat" for a weekday numbered 1 Sunday to 7 Saturday (Android: DateFormatSymbols.shortWeekdays). */
fun shortWeekday(weekday: Int): String = WEEKDAY_NAMES[weekday - 1].take(3)

/**
 * "Nov 5, 2026, 7:30 PM": when a class was finished, in the phone's [zone] (Android: DateFormat.getDateTimeInstance with
 * MEDIUM and SHORT in English, which writes the time in twelve hours whatever the phone's own setting).
 */
fun historyDate(millis: Long, zone: TimeZone): String {
    val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    val hour = time.hour % 12
    val minute = time.minute.toString().padStart(2, '0')
    return "${mediumDate(millis, zone)}, ${if (hour == 0) 12 else hour}:$minute ${if (time.hour < 12) "AM" else "PM"}"
}

/** "Rank 1" to "Rank 5", or "No rank yet" (Android: SkillsScreen.levelName). */
@Composable
fun levelName(level: SkillLevel): String = stringResource(when (level) {
    SkillLevel.NONE -> Res.string.skills_level_0
    SkillLevel.BEGINNER -> Res.string.skills_level_1
    SkillLevel.INTERMEDIATE -> Res.string.skills_level_2
    SkillLevel.ADVANCED -> Res.string.skills_level_3
    SkillLevel.MASTER -> Res.string.skills_level_4
    SkillLevel.ELITE -> Res.string.skills_level_5
})

/** Easy, Moderate, Hard, Very hard or Extreme: a skill's difficulty (Android: difficultyName). */
@Composable
fun difficultyName(grade: Int): String = stringResource(when (grade.coerceIn(1, 5)) {
    1 -> Res.string.skills_difficulty_1
    2 -> Res.string.skills_difficulty_2
    3 -> Res.string.skills_difficulty_3
    4 -> Res.string.skills_difficulty_4
    else -> Res.string.skills_difficulty_5
})

/** A set's exact reps or seconds: "8 reps", "5 reps each side", "30 s", "10 s each side" (Android: maxText). */
@Composable
fun maxText(standard: SkillStandard, amount: Int): String = when {
    standard.seconds -> stringResource(if (standard.perSide) Res.string.skills_max_seconds_side else Res.string.skills_max_seconds, amount)
    else -> pluralStringResource(if (standard.perSide) Res.plurals.skills_max_reps_side else Res.plurals.skills_max_reps, amount, amount)
}

/** A target: "12+ reps", "5+ reps each side", "60+ s" (Android: targetText). */
@Composable
fun targetText(standard: SkillStandard, amount: Int): String = when {
    standard.seconds -> stringResource(if (standard.perSide) Res.string.skills_target_seconds_side else Res.string.skills_target_seconds, amount)
    else -> pluralStringResource(if (standard.perSide) Res.plurals.skills_target_reps_side else Res.plurals.skills_target_reps, amount, amount)
}

/** "10 sets of 12+ reps": what a rank takes. */
@Composable
fun setsAt(sets: Int, standard: SkillStandard, target: Int): String =
    stringResource(Res.string.skills_sets_at, pluralStringResource(Res.plurals.skills_sets, sets, sets), targetText(standard, target))

/** What the next rank still takes: "18 sets of 12+ reps for Rank 2", or without the rank's name (Android: needText). */
@Composable
fun needText(status: SkillStatus, named: Boolean = true): String {
    val next = status.next ?: return levelName(status.level)
    val needs = setsAt(status.setsToGo, status.skill.standard, status.skill.standard.target(next) ?: 0)
    return if (named) stringResource(Res.string.skills_need_for, needs, levelName(next)) else needs
}

/** The equipment's name in Settings and on a left-out exercise, "Pull-up bar" (Android: equipmentName). */
fun equipmentName(equipment: Equipment): StringResource = when (equipment) {
    Equipment.PULLUP_BAR -> Res.string.equipment_pullup_bar
    Equipment.LOW_BAR -> Res.string.equipment_low_bar
    Equipment.PARALLEL_BARS -> Res.string.equipment_parallel_bars
    Equipment.BOX -> Res.string.equipment_box
    Equipment.WALL -> Res.string.equipment_wall
    Equipment.POLE -> Res.string.equipment_pole
    Equipment.BAND -> Res.string.equipment_band
}

/** What each piece of equipment serves, "Dips and support holds" (Android: equipmentHint). */
fun equipmentHint(equipment: Equipment): StringResource = when (equipment) {
    Equipment.PULLUP_BAR -> Res.string.equipment_pullup_bar_hint
    Equipment.LOW_BAR -> Res.string.equipment_low_bar_hint
    Equipment.PARALLEL_BARS -> Res.string.equipment_parallel_bars_hint
    Equipment.BOX -> Res.string.equipment_box_hint
    Equipment.WALL -> Res.string.equipment_wall_hint
    Equipment.POLE -> Res.string.equipment_pole_hint
    Equipment.BAND -> Res.string.equipment_band_hint
}
