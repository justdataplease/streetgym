package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.data.library.CustomDays
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * The history calendar's arithmetic. A day is its local date as yyyymmdd ([dayKey]) in the given time zone, so a class
 * finished at 23:50 stays on its evening and a daylight-saving day is still one day. A month is year * 12 + (month - 1)
 * ([monthOf]), so stepping months is plain addition.
 */
object HistoryCalendar {
    fun key(year: Int, month: Int, day: Int): Int = year * 10_000 + month * 100 + day

    fun dayKey(epochMillis: Long, zone: TimeZone): Int =
        GregorianCalendar(zone).apply { timeInMillis = epochMillis }.dayKey()

    fun monthOf(dayKey: Int): Int = dayKey / 10_000 * 12 + dayKey / 100 % 100 - 1
    fun year(month: Int): Int = month / 12
    /** 1 for January. */
    fun monthNumber(month: Int): Int = month % 12 + 1

    /**
     * Sessions by the local date they were finished, each day newest first as [sessions] are. A session without a real
     * date (none is stored today, but a mark could come without one) is left out.
     */
    fun byDay(sessions: List<WorkoutSession>, zone: TimeZone): Map<Int, List<WorkoutSession>> {
        val calendar = GregorianCalendar(zone)
        return sessions.filter { it.completedAtEpochMillis > 0L }.groupBy {
            calendar.timeInMillis = it.completedAtEpochMillis
            calendar.dayKey()
        }
    }

    /** The first month the calendar opens: the one of the oldest entry, marks included, or [current] with none. */
    fun firstMonth(days: Collection<Int>, current: Int): Int = days.minOrNull()?.let(::monthOf)?.coerceAtMost(current) ?: current

    /** One month in weeks: [leading] empty cells before day 1, then [days] days, in [rows] rows of seven. */
    data class MonthGrid(val month: Int, val firstDayOfWeek: Int, val leading: Int, val days: Int) {
        val rows: Int get() = (leading + days + 6) / 7
        /** The day of month in [cell] (row * 7 + column), or 0 for an empty cell. */
        fun dayAt(cell: Int): Int = (cell - leading + 1).takeIf { it in 1..days } ?: 0
        fun weekday(column: Int): Int = weekday(firstDayOfWeek, column)
    }

    /** Column [column]'s weekday in a week starting on [firstDayOfWeek], [Calendar.SUNDAY] to [Calendar.SATURDAY]. */
    fun weekday(firstDayOfWeek: Int, column: Int): Int = (firstDayOfWeek - 1 + column) % 7 + 1

    /** [month] laid out from [firstDayOfWeek] ([Calendar.SUNDAY] to [Calendar.SATURDAY]). Dates only: no time zone. */
    fun grid(month: Int, firstDayOfWeek: Int): MonthGrid {
        require(firstDayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY)
        val first = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year(month), month % 12, 1)
        }
        val weekday = first.get(Calendar.DAY_OF_WEEK)
        return MonthGrid(month, firstDayOfWeek, (weekday - firstDayOfWeek + 7) % 7, first.getActualMaximum(Calendar.DAY_OF_MONTH))
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
    fun monthMarks(byDay: Map<Int, List<WorkoutSession>>, month: Int, days: Int): Array<DayMarks?> =
        Array(days + 1) { day -> if (day == 0) null else byDay[key(year(month), monthNumber(month), day)]?.let(::marks) }

    /** Classes trained in the month: repeats and custom days count, marks don't. */
    fun trainedCount(marks: Array<DayMarks?>): Int = marks.sumOf { it?.trained ?: 0 }

    private fun Calendar.dayKey(): Int = key(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))
}
