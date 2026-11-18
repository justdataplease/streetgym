package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.HistoryCalendar
import com.justdataplease.streetgym.app.data.LocalDays
import com.justdataplease.streetgym.app.data.calendarDay
import com.justdataplease.streetgym.app.data.nextMidnight
import com.justdataplease.streetgym.data.progress.WorkoutSession
import kotlinx.datetime.TimeZone
import java.util.Calendar
import java.util.GregorianCalendar
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The calendar's and the streak's local days, written in common Kotlin for the iPhone, against Android's own code
 * (android-streetgym, HistoryCalendar.kt and StreakDays.kt, on java.util.Calendar), copied below as the reference: the
 * same day keys, local days, month grids and next midnights for the same times and zones, across midnight, month and
 * year ends and daylight-saving changes.
 */
class HistoryCalendarTest {
    private val zones = listOf("UTC", "Europe/Athens", "America/New_York", "America/Santiago", "Australia/Lord_Howe",
        "Asia/Kathmandu", "Pacific/Kiritimati", "America/St_Johns")

    /** Android's HistoryCalendar.dayKey. */
    private fun androidDayKey(millis: Long, zone: java.util.TimeZone): Int = GregorianCalendar(zone).apply { timeInMillis = millis }
        .let { HistoryCalendar.key(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }

    /** Android's StreakDays.epochDay. */
    private fun androidEpochDay(millis: Long, zone: java.util.TimeZone): Long = (millis + zone.getOffset(millis)).floorDiv(86_400_000L)

    /** Android's StreakDays.nextMidnight. */
    private fun androidNextMidnight(now: Long, zone: java.util.TimeZone): Long = GregorianCalendar(zone).apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Android's HistoryCalendar.grid. */
    private fun androidGrid(month: Int, firstDayOfWeek: Int): HistoryCalendar.MonthGrid {
        val first = GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(HistoryCalendar.year(month), month % 12, 1)
        }
        val weekday = first.get(Calendar.DAY_OF_WEEK)
        return HistoryCalendar.MonthGrid(month, firstDayOfWeek, (weekday - firstDayOfWeek + 7) % 7, first.getActualMaximum(Calendar.DAY_OF_MONTH))
    }

    private fun millis(zone: String, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(java.util.TimeZone.getTimeZone(zone)).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test
    fun everyHalfHourOfThreeYearsFallsOnAndroidsDay() {
        // 2025 to 2027 in half-hour steps, plus a quarter-hour offset for the zones that keep one.
        val start = millis("UTC", 2025, 1, 1, 0, 0) + 15 * 60_000L
        val end = millis("UTC", 2028, 1, 1, 0, 0)
        for (name in zones) {
            val reference = java.util.TimeZone.getTimeZone(name)
            val zone = TimeZone.of(name)
            var at = start
            while (at < end) {
                assertEquals(androidDayKey(at, reference), HistoryCalendar.dayKey(at, zone), "$name at $at")
                assertEquals(androidEpochDay(at, reference), LocalDays.epochDay(at, zone), "$name at $at")
                at += 30 * 60_000L
            }
        }
    }

    @Test
    fun daysTurnAtLocalMidnight() {
        val athens = TimeZone.of("Europe/Athens")
        // 23:50 stays on its evening; ten minutes past midnight is the next day.
        assertEquals(20_261_118, HistoryCalendar.dayKey(millis("Europe/Athens", 2026, 11, 18, 23, 50), athens))
        assertEquals(20_261_119, HistoryCalendar.dayKey(millis("Europe/Athens", 2026, 11, 19, 0, 10), athens))
        // The same instant is still the 18th in UTC (22:10).
        assertEquals(20_261_118, HistoryCalendar.dayKey(millis("Europe/Athens", 2026, 11, 19, 0, 10), TimeZone.UTC))
        // Month and year ends: 31 January and 31 December late in the evening, and the first minutes after.
        assertEquals(20_270_131, HistoryCalendar.dayKey(millis("Europe/Athens", 2027, 1, 31, 23, 59), athens))
        assertEquals(20_270_201, HistoryCalendar.dayKey(millis("Europe/Athens", 2027, 2, 1, 0, 0), athens))
        assertEquals(20_261_231, HistoryCalendar.dayKey(millis("America/New_York", 2026, 12, 31, 23, 30), TimeZone.of("America/New_York")))
        assertEquals(20_270_101, HistoryCalendar.dayKey(millis("America/New_York", 2027, 1, 1, 0, 5), TimeZone.of("America/New_York")))
    }

    @Test
    fun aDaylightSavingDayIsStillOneDay() {
        val athens = TimeZone.of("Europe/Athens")
        val reference = java.util.TimeZone.getTimeZone("Europe/Athens")
        // 29 March 2026: Athens skips from 03:00 to 04:00, a day of 23 hours.
        val before = millis("Europe/Athens", 2026, 3, 29, 2, 30)
        val after = millis("Europe/Athens", 2026, 3, 29, 23, 30)
        assertEquals(20_260_329, HistoryCalendar.dayKey(before, athens))
        assertEquals(20_260_329, HistoryCalendar.dayKey(after, athens))
        assertEquals(LocalDays.epochDay(before, athens), LocalDays.epochDay(after, athens))
        assertEquals(LocalDays.epochDay(before, athens) + 1, LocalDays.epochDay(after + 60 * 60_000L, athens))
        // Its next midnight is 23 hours after the one before; 25 October's is 25 hours after its own.
        val march = LocalDays.nextMidnight(millis("Europe/Athens", 2026, 3, 28, 12, 0), athens)
        assertEquals(23 * 3_600_000L, LocalDays.nextMidnight(march + 1, athens) - march)
        val october = LocalDays.nextMidnight(millis("Europe/Athens", 2026, 10, 24, 12, 0), athens)
        assertEquals(25 * 3_600_000L, LocalDays.nextMidnight(october + 1, athens) - october)
        // New York falls back on 1 November 2026: 01:30 comes twice, both on the 1st.
        val ny = TimeZone.of("America/New_York")
        val first = millis("America/New_York", 2026, 11, 1, 1, 30)
        assertEquals(20_261_101, HistoryCalendar.dayKey(first, ny))
        assertEquals(20_261_101, HistoryCalendar.dayKey(first + 3_600_000L, ny))
        assertEquals(androidNextMidnight(first, java.util.TimeZone.getTimeZone("America/New_York")), LocalDays.nextMidnight(first, ny))
        assertEquals(androidNextMidnight(march, reference), LocalDays.nextMidnight(march, athens))
    }

    @Test
    fun theNextMidnightIsAndroidsEvenWhereMidnightIsSkipped() {
        // Santiago springs forward at midnight (6 September 2026): that day starts at 01:00, as java.util.Calendar has it.
        for (name in zones) {
            val reference = java.util.TimeZone.getTimeZone(name)
            val zone = TimeZone.of(name)
            var at = millis("UTC", 2026, 1, 1, 0, 0) + 7 * 60_000L
            val end = millis("UTC", 2027, 1, 1, 0, 0)
            while (at < end) {
                assertEquals(androidNextMidnight(at, reference), LocalDays.nextMidnight(at, zone), "$name at $at")
                at += 3 * 3_600_000L + 11 * 60_000L
            }
        }
    }

    @Test
    fun monthGridsAreAndroids() {
        for (year in 2020..2032) for (number in 1..12) {
            val month = year * 12 + number - 1
            for (first in HistoryCalendar.SUNDAY..HistoryCalendar.SATURDAY) {
                assertEquals(androidGrid(month, first), HistoryCalendar.grid(month, first), "$year-$number from $first")
            }
        }
        // November 2026 starts on a Sunday: no empty cell from Sunday, six from Monday; 30 days in five or six rows.
        val november = 2026 * 12 + 10
        assertEquals(0, HistoryCalendar.grid(november, HistoryCalendar.SUNDAY).leading)
        assertEquals(6, HistoryCalendar.grid(november, 2).leading)
        assertEquals(5, HistoryCalendar.grid(november, HistoryCalendar.SUNDAY).rows)
        assertEquals(6, HistoryCalendar.grid(november, 2).rows)
        assertEquals(29, HistoryCalendar.grid(2028 * 12 + 1, 2).days)
        assertEquals(28, HistoryCalendar.grid(2100 * 12 + 1, 2).days)
        assertEquals(29, HistoryCalendar.grid(2000 * 12 + 1, 2).days)
    }

    @Test
    fun weekdayNumberingGoesBothWays() {
        for (iso in 1..7) assertEquals(iso, LocalDays.isoWeekday(LocalDays.calendarDay(iso)))
        assertEquals(Calendar.MONDAY, LocalDays.calendarDay(1))
        assertEquals(Calendar.SUNDAY, LocalDays.calendarDay(7))
    }

    @Test
    fun aMonthsMarksCountTrainedClassesNotMarks() {
        val zone = TimeZone.UTC
        val day = 86_400_000L
        val base = millis("UTC", 2026, 11, 5, 19, 30)
        val sessions = listOf(
            WorkoutSession("a", base - 40 * 60_000L, base, 2_400L),
            WorkoutSession("b", base, base + 60_000L, 60L, repeat = true),
            WorkoutSession("ready:neck", base, base + 120_000L, 60L),
            WorkoutSession("c", base + day, base + day, 0L, manuallyCompleted = true),
            WorkoutSession("d", base - 10 * day, base - 10 * day, 0L, manuallyCompleted = true, placed = true),
            WorkoutSession("e", 0L, 0L, 0L),
        )
        val byDay = HistoryCalendar.byDay(sessions, zone)
        assertEquals(setOf(20_261_105, 20_261_106, 20_261_026), byDay.keys)
        val november = HistoryCalendar.monthOf(20_261_105)
        val marks = HistoryCalendar.monthMarks(byDay, november, 30)
        assertEquals(HistoryCalendar.DayMarks(trained = 3, repeats = 1, custom = 1, marked = 0), marks[5])
        assertEquals(HistoryCalendar.DayMarks(trained = 0, repeats = 0, custom = 0, marked = 1), marks[6])
        assertEquals(3, HistoryCalendar.trainedCount(marks))
        // The calendar opens from the month of the oldest entry, a mark included.
        assertEquals(HistoryCalendar.monthOf(20_261_026), HistoryCalendar.firstMonth(byDay.keys, november))
    }
}
