package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.progress.WorkoutSession
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The history calendar's arithmetic (android-streetgym, data/progress/HistoryCalendar.kt, on java.util.Calendar there),
 * in plain Kotlin with kotlinx-datetime, with the same names and results. A day is its local date as yyyymmdd ([dayKey])
 * in the given time zone, so a class finished at 23:50 stays on its evening and a daylight-saving day is still one day.
 * A month is year * 12 + (month - 1) ([monthOf]), so stepping months is plain addition. Weekdays are numbered as
 * java.util.Calendar and NSCalendar number them: 1 Sunday to 7 Saturday ([SUNDAY], [SATURDAY]).
 */
object HistoryCalendar {
    const val SUNDAY = 1
    const val SATURDAY = 7

    fun key(year: Int, month: Int, day: Int): Int = year * 10_000 + month * 100 + day

    fun dayKey(epochMillis: Long, zone: TimeZone): Int =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date.let { key(it.year, it.month.ordinal + 1, it.day) }

    fun monthOf(dayKey: Int): Int = dayKey / 10_000 * 12 + dayKey / 100 % 100 - 1
    fun year(month: Int): Int = month / 12
    /** 1 for January. */
    fun monthNumber(month: Int): Int = month % 12 + 1

    /**
     * Sessions by the local date they were finished, each day newest first as [sessions] are. A session without a real
     * date (none is stored today, but a mark could come without one) is left out.
     */
    fun byDay(sessions: List<WorkoutSession>, zone: TimeZone): Map<Int, List<WorkoutSession>> =
        sessions.filter { it.completedAtEpochMillis > 0L }.groupBy { dayKey(it.completedAtEpochMillis, zone) }

    /** The first month the calendar opens: the one of the oldest entry, marks included, or [current] with none. */
    fun firstMonth(days: Collection<Int>, current: Int): Int = days.minOrNull()?.let(::monthOf)?.coerceAtMost(current) ?: current

    /** One month in weeks: [leading] empty cells before day 1, then [days] days, in [rows] rows of seven. */
    data class MonthGrid(val month: Int, val firstDayOfWeek: Int, val leading: Int, val days: Int) {
        val rows: Int get() = (leading + days + 6) / 7
        /** The day of month in [cell] (row * 7 + column), or 0 for an empty cell. */
        fun dayAt(cell: Int): Int = (cell - leading + 1).takeIf { it in 1..days } ?: 0
        fun weekday(column: Int): Int = weekday(firstDayOfWeek, column)
    }

    /** Column [column]'s weekday in a week starting on [firstDayOfWeek], [SUNDAY] to [SATURDAY]. */
    fun weekday(firstDayOfWeek: Int, column: Int): Int = (firstDayOfWeek - 1 + column) % 7 + 1

    /** A date's weekday, [SUNDAY] to [SATURDAY]. */
    fun weekdayOf(date: LocalDate): Int = calendarDay(date.dayOfWeek)

    /** [day] as java.util.Calendar numbers it: 1 Sunday to 7 Saturday. */
    fun calendarDay(day: DayOfWeek): Int = day.isoDayNumber % 7 + 1

    /** [month] laid out from [firstDayOfWeek] ([SUNDAY] to [SATURDAY]). Dates only: no time zone. */
    fun grid(month: Int, firstDayOfWeek: Int): MonthGrid {
        require(firstDayOfWeek in SUNDAY..SATURDAY)
        val year = year(month)
        val number = monthNumber(month)
        val weekday = weekdayOf(LocalDate(year, number, 1))
        return MonthGrid(month, firstDayOfWeek, (weekday - firstDayOfWeek + 7) % 7, daysIn(year, number))
    }

    /** The days of [month] (1 January) in [year], by the Gregorian rules. */
    fun daysIn(year: Int, month: Int): Int = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /**
     * What one day holds. [trained] counts every class finished, repeats and custom days included; [marked] are days set
     * done by Complete day, Complete level or the placement, which were not trained and count toward nothing here.
     */
    data class DayMarks(val trained: Int, val repeats: Int, val custom: Int, val marked: Int) {
        /** Program days trained for the first time. */
        val workouts: Int get() = trained - repeats - custom
    }

    fun marks(sessions: List<WorkoutSession>): DayMarks {
        var trained = 0
        var repeats = 0
        var custom = 0
        var marked = 0
        sessions.forEach {
            when {
                it.manuallyCompleted -> marked++
                else -> {
                    trained++
                    if (it.repeat) repeats++ else if (CustomDays.offProgram(it.programId)) custom++
                }
            }
        }
        return DayMarks(trained, repeats, custom, marked)
    }

    /** [month]'s days, indexed by day of month (index 0 unused), each null when nothing is recorded that day. */
    fun monthMarks(byDay: Map<Int, List<WorkoutSession>>, month: Int, days: Int): List<DayMarks?> =
        List(days + 1) { day -> if (day == 0) null else byDay[key(year(month), monthNumber(month), day)]?.let(::marks) }

    /** Classes trained in the month: repeats and custom days count, marks don't. */
    fun trainedCount(marks: List<DayMarks?>): Int = marks.sumOf { it?.trained ?: 0 }

    /** The date of a day key. */
    fun date(dayKey: Int): LocalDate = LocalDate(dayKey / 10_000, dayKey / 100 % 100, dayKey % 100)
}

/** The first instant of the local day after [now] in [zone]: when an unlit flame's day is over (Android: StreakDays.nextMidnight). */
fun LocalDays.nextMidnight(now: Long, zone: TimeZone): Long {
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    return LocalDate.fromEpochDays(today.toEpochDays() + 1).atStartOfDayIn(zone).toEpochMilliseconds()
}

/** [Streak]'s 1 Monday to 7 Sunday as 1 Sunday to 7 Saturday (Android: StreakDays.calendarDay). */
fun LocalDays.calendarDay(isoWeekday: Int): Int = isoWeekday % 7 + 1
