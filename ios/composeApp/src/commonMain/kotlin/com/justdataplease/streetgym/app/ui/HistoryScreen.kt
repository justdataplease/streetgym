package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.HistoryCalendar
import com.justdataplease.streetgym.app.data.HistoryCalendar.DayMarks
import com.justdataplease.streetgym.app.data.LocalDays
import com.justdataplease.streetgym.app.data.calendarDay
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.calendar_custom_description
import com.justdataplease.streetgym.app.resources.calendar_day_empty
import com.justdataplease.streetgym.app.resources.calendar_empty
import com.justdataplease.streetgym.app.resources.calendar_legend_custom
import com.justdataplease.streetgym.app.resources.calendar_legend_marked
import com.justdataplease.streetgym.app.resources.calendar_legend_repeat
import com.justdataplease.streetgym.app.resources.calendar_legend_workout
import com.justdataplease.streetgym.app.resources.calendar_marked_days
import com.justdataplease.streetgym.app.resources.calendar_marked_description
import com.justdataplease.streetgym.app.resources.calendar_month_count
import com.justdataplease.streetgym.app.resources.calendar_month_count_current
import com.justdataplease.streetgym.app.resources.calendar_month_empty
import com.justdataplease.streetgym.app.resources.calendar_next_month
import com.justdataplease.streetgym.app.resources.calendar_no_workouts_description
import com.justdataplease.streetgym.app.resources.calendar_previous_month
import com.justdataplease.streetgym.app.resources.calendar_repeats_description
import com.justdataplease.streetgym.app.resources.calendar_today_description
import com.justdataplease.streetgym.app.resources.calendar_whole_month
import com.justdataplease.streetgym.app.resources.calendar_workouts_description
import com.justdataplease.streetgym.app.resources.completed_on
import com.justdataplease.streetgym.app.resources.custom_deleted
import com.justdataplease.streetgym.app.resources.history_placed
import com.justdataplease.streetgym.app.resources.history_repeat
import com.justdataplease.streetgym.app.resources.intensity_easier
import com.justdataplease.streetgym.app.resources.intensity_harder
import com.justdataplease.streetgym.app.theme.Palette
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.blendColor
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.WorkoutSession
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.min

/** "Marked complete · …": written in Android's screen (historyRow) rather than its string files. */
internal const val MARKED_COMPLETE = "Marked complete"

/**
 * The calendar's month and picked day while the app runs, so coming back from a class returns to them (Android:
 * HistoryCalendarState). [month] null is the current one; [day] is the picked day as yyyymmdd, 0 for none.
 */
@Stable
class HistoryCalendarState(month: Int? = null, day: Int = 0) {
    var month by mutableStateOf(month)
    var day by mutableIntStateOf(day)
}

/** The arrows, or a sideways swipe this long, step the month. */
private val SWIPE = 56.dp

/**
 * Progress › History (android-streetgym, ui/HistoryCalendarView.kt): the month with its classes listed under it.
 * Trained days are filled, a repeat adds a ring under the day and an own workout a diamond, a day only marked complete
 * is pale, today is ringed and the picked day sits on a halo. The arrows or a sideways swipe step between the month of
 * the oldest entry and this one. Tapping a day lists only its classes; tapping it again, or All of the month, brings the
 * month back. A row opens its class ([onOpenClass]); the streak's line opens the streak's sheet ([onStreak]).
 */
@Composable
fun HistorySection(
    model: AppModel,
    state: HistoryCalendarState,
    onOpenClass: (String) -> Unit,
    onStreak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Street.colors
    val zone = model.zone
    val history = model.history()
    val byDay = remember(history, zone) { HistoryCalendar.byDay(history, zone) }
    val today = model.todayKey()
    val current = HistoryCalendar.monthOf(today)
    val first = HistoryCalendar.firstMonth(byDay.keys, current)
    val month = (state.month ?: current).coerceIn(first, current)
    fun show(target: Int) {
        if (target in first..current) state.month = target.takeIf { it != current }
    }
    val year = HistoryCalendar.year(month)
    val number = HistoryCalendar.monthNumber(month)
    val grid = HistoryCalendar.grid(month, LocalDays.calendarDay(model.firstWeekday))
    val marks = HistoryCalendar.monthMarks(byDay, month, grid.days)
    val trained = HistoryCalendar.trainedCount(marks)
    val colors = calendarColors(palette)
    // The streak is about now: it shows with this month only, and not before the first workout.
    val streak = model.streak().streak.takeIf { month == current && it.best > 0 }

    Column(modifier.fillMaxWidth()) {
        StreetCard(padding = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthArrow("‹", stringResource(Res.string.calendar_previous_month), enabled = month > first) { show(month - 1) }
                TitleText(calendarTitle(month), 17f, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    align = TextAlign.Center, maxLines = 1)
                MonthArrow("›", stringResource(Res.string.calendar_next_month), enabled = month < current) { show(month + 1) }
            }
            BodyText(
                if (month == current) pluralStringResource(Res.plurals.calendar_month_count_current, trained, trained)
                else pluralStringResource(Res.plurals.calendar_month_count, trained, trained, monthName(month)),
                14f, Modifier.fillMaxWidth().padding(bottom = if (streak == null) 10.dp else 2.dp), align = TextAlign.Center,
            )
            if (streak != null) StreakLine(streak, Modifier.fillMaxWidth().padding(bottom = 6.dp), onClick = onStreak)
            // The week starts where the phone's region starts it; each day's description already names its date.
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                for (column in 0 until 7) BodyText(shortWeekday(grid.weekday(column)), 12f, Modifier.weight(1f), bold = true,
                    align = TextAlign.Center, maxLines = 1)
            }
            Column(
                Modifier.padding(top = 4.dp).pointerInput(month, first, current) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = { if (abs(dragged) >= SWIPE.toPx()) show(month + if (dragged < 0) 1 else -1) },
                    ) { change, amount ->
                        change.consume()
                        dragged += amount
                    }
                },
            ) {
                for (row in 0 until grid.rows) Row(Modifier.fillMaxWidth()) {
                    for (column in 0 until 7) {
                        val day = grid.dayAt(row * 7 + column)
                        if (day == 0) {
                            Spacer(Modifier.weight(1f).height(52.dp))
                            continue
                        }
                        val key = HistoryCalendar.key(year, number, day)
                        val open = key <= today
                        val description = if (open) describe(key, marks[day], key == today) else dayTitle(key)
                        DayCell(
                            day, marks[day], today = key == today, open = open, selected = key == state.day, colors = colors,
                            modifier = Modifier.weight(1f).height(52.dp)
                                .clickable(enabled = open) { state.day = if (key == state.day) 0 else key }
                                .semantics {
                                    contentDescription = description
                                    selected = key == state.day
                                },
                        )
                    }
                }
            }
            Legend(colors, Modifier.padding(start = 8.dp, top = 12.dp, end = 8.dp))
        }
        HistoryList(model, byDay, month, state, onOpenClass, Modifier.padding(top = 14.dp))
    }
}

/** ‹ or ›: 48 dp, faded when there is no month that way. */
@Composable
private fun MonthArrow(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).alpha(if (enabled) 1f else 0.25f).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = TextStyle(color = Street.colors.text, fontSize = 30.sp, fontFamily = Street.type.body))
    }
}

/**
 * The picked day's classes, or else the whole month's, newest day first (Android: bindPanel). A placement marks whole
 * levels on one day, so a day with more than three marks shows them as one line.
 */
@Composable
private fun HistoryList(
    model: AppModel,
    byDay: Map<Int, List<WorkoutSession>>,
    month: Int,
    state: HistoryCalendarState,
    onOpenClass: (String) -> Unit,
    modifier: Modifier,
) {
    val picked = state.day.takeIf { it != 0 && HistoryCalendar.monthOf(it) == month }
    val name = monthName(month)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(if (picked != null) weekdayTitle(picked) else name, Modifier.weight(1f))
            if (picked != null) Box(
                Modifier.heightIn(min = 40.dp).clickable(role = Role.Button) { state.day = 0 }.padding(start = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BodyText(stringResource(Res.string.calendar_whole_month, name), 13f, bold = true, color = Street.colors.text, maxLines = 1)
            }
        }
        val days = if (picked != null) listOf(picked) else byDay.keys.filter { HistoryCalendar.monthOf(it) == month }.sortedDescending()
        var listed = false
        days.forEach { day ->
            val sessions = byDay[day].orEmpty().sortedByDescending(WorkoutSession::completedAtEpochMillis)
            val (marked, trained) = sessions.partition(WorkoutSession::manuallyCompleted)
            trained.forEach { HistoryRow(model, it, onOpenClass) }
            if (marked.size <= 3) marked.forEach { HistoryRow(model, it, onOpenClass) }
            else BodyText(pluralStringResource(Res.plurals.calendar_marked_days, marked.size, marked.size), 14f, Modifier.padding(bottom = 8.dp))
            listed = listed || sessions.isNotEmpty()
        }
        if (!listed) BodyText(stringResource(when {
            picked != null -> Res.string.calendar_day_empty
            byDay.isEmpty() -> Res.string.calendar_empty
            else -> Res.string.calendar_month_empty
        }), 14f)
    }
}

/**
 * One history entry: the class and when it was finished, "Repeat · …", "· Harder", "Placed by your test · …" or "Marked
 * complete · …"; a tap opens the class (Android: HomeActivity.historyRow).
 */
@Composable
private fun HistoryRow(model: AppModel, session: WorkoutSession, onOpenClass: (String) -> Unit) {
    val palette = Street.colors
    val program = model.programById(session.programId)
    val date = historyDate(session.completedAtEpochMillis, model.zone)
    val title = program?.let { displayName(it) }
        ?: if (CustomDays.isCustom(session.programId)) stringResource(Res.string.custom_deleted) else session.programId
    val body = when {
        session.placed -> stringResource(Res.string.history_placed, date)
        session.manuallyCompleted -> "$MARKED_COMPLETE · $date"
        else -> {
            val done = stringResource(Res.string.completed_on, date, (session.durationSeconds / 60L).toInt())
            val repeat = if (session.repeat) stringResource(Res.string.history_repeat, done) else done
            when (session.intensity) {
                Intensity.NORMAL -> repeat
                Intensity.EASIER -> "$repeat · ${stringResource(Res.string.intensity_easier)}"
                Intensity.HARDER -> "$repeat · ${stringResource(Res.string.intensity_harder)}"
            }
        }
    }
    StreetCard(Modifier.padding(bottom = 8.dp), onClick = program?.let { { onOpenClass(it.id) } }) {
        TitleText(title, 17f)
        BodyText(body, 13f, Modifier.padding(top = 3.dp), color = if (session.repeat) palette.primary else palette.textSecondary)
    }
}

/** "November 5, today, 2 workouts, 1 repeat": what a day shows, for VoiceOver (Android: describe). */
@Composable
private fun describe(key: Int, marks: DayMarks?, today: Boolean): String {
    val trained = marks?.trained ?: 0
    val marked = marks?.marked ?: 0
    return listOfNotNull(
        dayTitle(key),
        stringResource(Res.string.calendar_today_description).takeIf { today },
        when {
            trained > 0 -> pluralStringResource(Res.plurals.calendar_workouts_description, trained, trained)
            marked == 0 -> stringResource(Res.string.calendar_no_workouts_description)
            else -> null
        },
        marks?.repeats?.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.calendar_repeats_description, it, it) },
        marks?.custom?.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.calendar_custom_description, it, it) },
        marked.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.calendar_marked_description, it, it) },
    ).joinToString(", ")
}

/** The calendar's colours in the active theme (Android: CalendarPaints). */
private class CalendarColors(
    val trained: Color,
    val onTrained: Color,
    val marked: Color,
    val repeat: Color,
    val custom: Color,
    val today: Color,
    val halo: Color,
    val number: Color,
    val faint: Color,
    val future: Color,
)

private fun calendarColors(palette: Palette) = CalendarColors(
    trained = palette.primary,
    onTrained = palette.onAccent,
    marked = blendColor(palette.primary, palette.surface, 0.78f),
    repeat = palette.primary,
    custom = palette.text,
    today = palette.text,
    halo = blendColor(palette.accent, palette.surface, 0.3f),
    number = palette.text,
    faint = palette.textSecondary,
    future = blendColor(palette.textSecondary, palette.surface, 0.55f),
)

/** A day's shapes are centred 22 dp down; the repeat's ring and the own workout's diamond 47 dp down. */
private val DISC_Y = 22.dp
private val MARKER_Y = 47.dp
private val MARKER = 3.2.dp

/** One day of the grid, drawn from its marks (Android: DayCell). */
@Composable
private fun DayCell(day: Int, marks: DayMarks?, today: Boolean, open: Boolean, selected: Boolean, colors: CalendarColors, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val type = Street.type
    // Follows the font scale, up to where a number still fits its disc.
    val textSize = with(LocalDensity.current) { min(14.sp.toPx(), 18.dp.toPx()).toSp() }
    Canvas(modifier) {
        val x = size.width / 2f
        val y = DISC_Y.toPx()
        val line = 2.dp.toPx()
        val gap = 3.dp.toPx()
        val radius = min(15.dp.toPx(), x - gap - line)
        if (selected) drawCircle(colors.halo, min(21.dp.toPx(), x - 2.dp.toPx()), Offset(x, y))
        val trained = marks != null && marks.trained > 0
        val markedOnly = marks != null && !trained && marks.marked > 0
        if (trained || markedOnly) drawCircle(if (trained) colors.trained else colors.marked, radius, Offset(x, y))
        if (today) drawCircle(colors.today, radius + gap, Offset(x, y), style = Stroke(line))
        val color = when {
            trained -> colors.onTrained
            markedOnly -> colors.faint
            !open -> colors.future
            else -> colors.number
        }
        val text = measurer.measure(day.toString(), TextStyle(color = color, fontSize = textSize,
            fontFamily = if (trained || today) type.bold else type.body), maxLines = 1)
        drawText(text, topLeft = Offset(x - text.size.width / 2f, y - text.size.height / 2f))
        if (marks == null) return@Canvas
        val repeat = marks.repeats > 0
        val custom = marks.custom > 0
        val offset = if (repeat && custom) 5.5.dp.toPx() else 0f
        if (repeat) drawCircle(colors.repeat, MARKER.toPx(), Offset(x - offset, MARKER_Y.toPx()), style = Stroke(line))
        if (custom) diamond(colors.custom, Offset(x + offset, MARKER_Y.toPx()))
    }
}

/** An own workout's mark: a small square on its corner. */
private fun DrawScope.diamond(color: Color, centre: Offset) {
    val half = MARKER.toPx() * 0.9f
    rotate(45f, centre) { drawRect(color, Offset(centre.x - half, centre.y - half), Size(half * 2f, half * 2f)) }
}

private enum class Marker { WORKOUT, REPEAT, CUSTOM, MARKED }

/** Workout, Repeat, Your workout, Marked complete: the shapes the cells draw, two to a row (Android: legend). */
@Composable
private fun Legend(colors: CalendarColors, modifier: Modifier) {
    val rows = listOf(
        listOf(Marker.WORKOUT to Res.string.calendar_legend_workout, Marker.REPEAT to Res.string.calendar_legend_repeat),
        listOf(Marker.CUSTOM to Res.string.calendar_legend_custom, Marker.MARKED to Res.string.calendar_legend_marked),
    )
    Column(modifier.fillMaxWidth()) {
        rows.forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { (marker, label) ->
                    Row(Modifier.weight(1f).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(16.dp)) {
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            when (marker) {
                                Marker.WORKOUT, Marker.MARKED -> drawCircle(if (marker == Marker.WORKOUT) colors.trained else colors.marked,
                                    min(centre.x, centre.y) - 1.dp.toPx(), centre)
                                Marker.REPEAT -> drawCircle(colors.repeat, MARKER.toPx(), centre, style = Stroke(2.dp.toPx()))
                                Marker.CUSTOM -> diamond(colors.custom, centre)
                            }
                        }
                        BodyText(stringResource(label), 12f, Modifier.padding(start = 6.dp), maxLines = 1)
                    }
                }
            }
        }
    }
}
