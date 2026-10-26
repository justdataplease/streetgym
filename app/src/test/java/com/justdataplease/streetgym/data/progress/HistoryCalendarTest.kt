package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.data.progress.HistoryCalendar.DayMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

class HistoryCalendarTest {
    private val athens = TimeZone.getTimeZone("Europe/Athens")
    private val newYork = TimeZone.getTimeZone("America/New_York")

    /** The instant of a local wall-clock time, worked out independently with java.time. */
    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0, nanos: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute, second, nanos).atZone(ZoneId.of(zone.id)).toInstant().toEpochMilli()

    private fun session(at: Long, id: String = "sg:l01:p0001", repeat: Boolean = false, manual: Boolean = false) =
        WorkoutSession(id, at, at, 0L, manuallyCompleted = manual, repeat = repeat)

    @Test
    fun midnightSplitsDaysInLocalTime() {
        assertEquals(20261024, HistoryCalendar.dayKey(at(athens, 2026, 10, 24, 23, 59, 59, 999_000_000), athens))
        assertEquals(20261025, HistoryCalendar.dayKey(at(athens, 2026, 10, 25, 0, 0), athens))
        // New Year's Eve stays in its year and month.
        assertEquals(20251231, HistoryCalendar.dayKey(at(athens, 2025, 12, 31, 23, 59), athens))
        assertEquals(20260101, HistoryCalendar.dayKey(at(athens, 2026, 1, 1, 0, 0), athens))
    }

    @Test
    fun theSameInstantIsTheLocalDateOfEachZone() {
        // 02:30 UTC on October 26: 04:30 that day in Athens, still 22:30 on the 25th in New York.
        val instant = at(TimeZone.getTimeZone("UTC"), 2026, 10, 26, 2, 30)
        assertEquals(20261026, HistoryCalendar.dayKey(instant, athens))
        assertEquals(20261025, HistoryCalendar.dayKey(instant, newYork))
        val sessions = listOf(session(instant))
        assertEquals(setOf(20261026), HistoryCalendar.byDay(sessions, athens).keys)
        assertEquals(setOf(20261025), HistoryCalendar.byDay(sessions, newYork).keys)
    }

    @Test
    fun daylightSavingDaysAreStillOneDay() {
        // October 25, 2026 in Athens has 25 hours (04:00 summer time becomes 03:00); a fixed summer offset would put
        // its late evening on the 26th.
        val endOfSummer = listOf(
            at(athens, 2026, 10, 25, 0, 30),
            at(athens, 2026, 10, 25, 3, 30),
            at(athens, 2026, 10, 25, 23, 30),
        ).map(::session)
        assertEquals(mapOf(20261025 to endOfSummer), HistoryCalendar.byDay(endOfSummer, athens))
        assertEquals(20261026, HistoryCalendar.dayKey(at(athens, 2026, 10, 26, 0, 10), athens))
        // March 29, 2026 has 23 hours (03:00 becomes 04:00).
        assertEquals(20260328, HistoryCalendar.dayKey(at(athens, 2026, 3, 28, 23, 59), athens))
        assertEquals(20260329, HistoryCalendar.dayKey(at(athens, 2026, 3, 29, 0, 0), athens))
        assertEquals(20260329, HistoryCalendar.dayKey(at(athens, 2026, 3, 29, 23, 59), athens))
        assertEquals(20260330, HistoryCalendar.dayKey(at(athens, 2026, 3, 30, 0, 0), athens))
        // New York's own change, November 1, 2026.
        assertEquals(20261101, HistoryCalendar.dayKey(at(newYork, 2026, 11, 1, 23, 45), newYork))
    }

    @Test
    fun groupingKeepsNewestFirstAndDropsUndatedEntries() {
        val evening = session(at(athens, 2026, 10, 20, 19, 0))
        val morning = session(at(athens, 2026, 10, 20, 7, 0))
        val undated = WorkoutSession("sg:l01:p0002", 0L, 0L, 0L, manuallyCompleted = true)
        val grouped = HistoryCalendar.byDay(listOf(evening, morning, undated), athens)
        assertEquals(mapOf(20261020 to listOf(evening, morning)), grouped)
    }

    @Test
    fun monthGridFollowsTheFirstWeekday() {
        val october = HistoryCalendar.monthOf(20261001)
        // October 1, 2026 is a Thursday.
        val sunday = HistoryCalendar.grid(october, Calendar.SUNDAY)
        assertEquals(4, sunday.leading)
        assertEquals(31, sunday.days)
        assertEquals(5, sunday.rows)
        assertEquals(0, sunday.dayAt(3))
        assertEquals(1, sunday.dayAt(4))
        assertEquals(31, sunday.dayAt(34))
        assertEquals(0, sunday.dayAt(35))
        assertEquals(Calendar.SUNDAY, sunday.weekday(0))
        assertEquals(Calendar.THURSDAY, sunday.weekday(4))

        val monday = HistoryCalendar.grid(october, Calendar.MONDAY)
        assertEquals(3, monday.leading)
        assertEquals(5, monday.rows)
        assertEquals(Calendar.MONDAY, monday.weekday(0))
        assertEquals(Calendar.SUNDAY, monday.weekday(6))

        val saturday = HistoryCalendar.grid(october, Calendar.SATURDAY)
        assertEquals(5, saturday.leading)
        assertEquals(6, saturday.rows)
        assertEquals(Calendar.FRIDAY, saturday.weekday(6))
    }

    @Test
    fun shortAndLeapFebruaries() {
        // February 1, 2026 is a Sunday: four full weeks from Sunday, five from Monday.
        val february = HistoryCalendar.monthOf(20260201)
        assertEquals(0, HistoryCalendar.grid(february, Calendar.SUNDAY).leading)
        assertEquals(4, HistoryCalendar.grid(february, Calendar.SUNDAY).rows)
        assertEquals(6, HistoryCalendar.grid(february, Calendar.MONDAY).leading)
        assertEquals(5, HistoryCalendar.grid(february, Calendar.MONDAY).rows)
        assertEquals(29, HistoryCalendar.grid(HistoryCalendar.monthOf(20280201), Calendar.MONDAY).days)
    }

    @Test
    fun monthsStepAcrossYears() {
        val january = HistoryCalendar.monthOf(20260115)
        assertEquals(2025, HistoryCalendar.year(january - 1))
        assertEquals(12, HistoryCalendar.monthNumber(january - 1))
        assertEquals(2026, HistoryCalendar.year(january))
        assertEquals(1, HistoryCalendar.monthNumber(january))
        assertEquals(january + 11, HistoryCalendar.monthOf(20261231))
    }

    @Test
    fun theMonthlyCountLeavesOutMarks() {
        val sessions = listOf(
            session(at(athens, 2026, 10, 25, 18, 0)),
            session(at(athens, 2026, 10, 25, 9, 0), repeat = true),
            session(at(athens, 2026, 10, 3, 8, 0), id = "custom:abc"),
            session(at(athens, 2026, 10, 1, 12, 0), manual = true),
            session(at(athens, 2026, 10, 1, 12, 0), id = "sg:l01:p0002", manual = true),
            session(at(athens, 2026, 10, 1, 12, 0), id = "sg:l01:p0003", manual = true),
            session(at(athens, 2026, 9, 30, 23, 30)),
        )
        val byDay = HistoryCalendar.byDay(sessions, athens)
        val october = HistoryCalendar.monthOf(20261001)
        val marks = HistoryCalendar.monthMarks(byDay, october, 31)
        assertEquals(32, marks.size)
        assertEquals(3, HistoryCalendar.trainedCount(marks))
        assertEquals(DayMarks(trained = 2, repeats = 1, custom = 0, marked = 0), marks[25])
        assertEquals(1, marks[25]!!.workouts)
        assertEquals(DayMarks(trained = 1, repeats = 0, custom = 1, marked = 0), marks[3])
        assertEquals(DayMarks(trained = 0, repeats = 0, custom = 0, marked = 3), marks[1])
        assertNull(marks[2])
        assertNull(marks[0])
        assertEquals(1, HistoryCalendar.trainedCount(HistoryCalendar.monthMarks(byDay, october - 1, 30)))
    }

    @Test
    fun theCalendarOpensFromTheOldestEntryUpToThisMonth() {
        val current = HistoryCalendar.monthOf(20261026)
        assertEquals(current, HistoryCalendar.firstMonth(emptySet(), current))
        assertEquals(current - 2, HistoryCalendar.firstMonth(setOf(20261003, 20260815), current))
        // A clock set ahead never opens a month past this one.
        assertEquals(current, HistoryCalendar.firstMonth(setOf(20270105), current))
    }
}
