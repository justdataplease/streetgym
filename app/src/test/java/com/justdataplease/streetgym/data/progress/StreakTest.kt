package com.justdataplease.streetgym.data.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

class StreakTest {
    private val athens = TimeZone.getTimeZone("Europe/Athens")
    private val newYork = TimeZone.getTimeZone("America/New_York")

    /** Thursday, November 12, 2026 as a local day. */
    private val today = LocalDate.of(2026, 11, 12).toEpochDay()

    private fun days(vararg ago: Int): List<Long> = ago.map { today - it }
    private fun run(from: Int, to: Int): List<Long> = (to..from).map { today - it }

    /** The instant of a local wall-clock time, worked out independently with java.time. */
    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of(zone.id)).toInstant().toEpochMilli()

    private fun session(at: Long, id: String = "sg:l01:p0001", repeat: Boolean = false, manual: Boolean = false) =
        WorkoutSession(id, at, at, 0L, manuallyCompleted = manual, repeat = repeat)

    @Test
    fun noWorkoutsNoStreak() {
        assertEquals(Streak.NONE, Streak.of(emptyList(), today))
        assertEquals(Streak(0, 0, false), Streak.NONE)
    }

    @Test
    fun todaysWorkoutLightsTheFlame() {
        assertEquals(Streak(1, 1, true), Streak.of(days(0), today))
        assertEquals(Streak(12, 12, true), Streak.of(run(11, 0), today))
    }

    @Test
    fun theNumberIsKeptWhileTheStreakIsAlive() {
        // Trained up to yesterday: unlit, yesterday's number, and today may be the rest day.
        assertEquals(Streak(1, 1, false, restToday = true), Streak.of(days(1), today))
        assertEquals(Streak(11, 11, false, restToday = true), Streak.of(run(11, 1), today))
        // The same history a day later: yesterday was the rest day, so today's workout is due.
        assertEquals(Streak(11, 11, false), Streak.of(run(11, 1), today + 1))
        // A third day without a workout: the streak is gone, the best one stays.
        assertEquals(Streak(0, 11, false), Streak.of(run(11, 1), today + 2))
    }

    @Test
    fun oneRestDayKeepsTheStreak() {
        // Train, rest, train, rest, train.
        assertEquals(Streak(3, 3, true), Streak.of(days(4, 2, 0), today))
        // Three days, a day off, then today.
        assertEquals(Streak(4, 4, true), Streak.of(days(4, 3, 2, 0), today))
        // Every other day for three weeks: only the days trained are counted.
        assertEquals(Streak(11, 11, true), Streak.of((0..20 step 2).map { today - it }, today))
        // A rest day adds nothing: the number is the same on the day off and on the day the workout is due.
        assertEquals(Streak(3, 3, false, restToday = true), Streak.of(days(5, 3, 1), today))
        assertEquals(Streak(3, 3, false), Streak.of(days(6, 4, 2), today))
    }

    @Test
    fun twoMissedDaysStartAgainAtOne() {
        // Three days, two days off, then today.
        assertEquals(Streak(1, 3, true), Streak.of(days(5, 4, 3, 0), today))
        // The last workout was three days ago.
        assertEquals(Streak(0, 1, false), Streak.of(days(3), today))
        // Two days off in the middle split the runs; one day off does not.
        assertEquals(Streak(3, 3, true), Streak.of(days(9, 7, 4, 2, 0), today))
    }

    @Test
    fun theBestStreakIsKept() {
        val history = run(40, 20) + run(11, 0)
        assertEquals(Streak(12, 21, true), Streak.of(history, today))
        // The current run becomes the best once it is the longest.
        assertEquals(Streak(22, 22, true), Streak.of(run(60, 40) + run(21, 0), today))
        assertEquals(Streak(41, 41, true), Streak.of(run(40, 0), today))
        // The best run may have rest days in it too.
        assertEquals(Streak(1, 4, true), Streak.of(days(30, 28, 27, 25, 0), today))
    }

    @Test
    fun severalWorkoutsInADayCountOnce() {
        assertEquals(Streak(2, 2, true), Streak.of(days(1, 1, 0, 0, 0), today))
    }

    @Test
    fun aDayAfterTodayCountsForTheBestOnly() {
        // The clock was set back: a workout is dated tomorrow.
        assertEquals(Streak(2, 3, true), Streak.of(days(1, 0, -1), today))
        assertEquals(Streak(0, 1, false), Streak.of(days(-1), today))
    }

    @Test
    fun weekdaysCountFromThursday1970() {
        for (day in listOf(-8L, -1L, 0L, 1L, 3L, 4L, today, today + 3)) {
            assertEquals(LocalDate.ofEpochDay(day).dayOfWeek.value, Streak.weekday(day))
        }
        assertEquals(DayOfWeek.THURSDAY.value, Streak.weekday(today))
    }

    @Test
    fun theWeekRowRunsFromTheRegionsFirstDay() {
        val trained = days(3, 2, 1)
        val monday = Streak.week(trained, today, firstWeekday = 1)
        assertEquals(LocalDate.of(2026, 11, 9).toEpochDay(), monday.first().epochDay)
        assertEquals(LocalDate.of(2026, 11, 15).toEpochDay(), monday.last().epochDay)
        assertEquals(listOf(true, true, true, false, false, false, false), monday.map { it.trained })
        assertEquals(listOf(false, false, false, true, false, false, false), monday.map { it.today })
        assertEquals(listOf(true, true, true, true, false, false, false), monday.map { it.open })

        val sunday = Streak.week(trained, today, firstWeekday = 7)
        assertEquals(LocalDate.of(2026, 11, 8).toEpochDay(), sunday.first().epochDay)
        assertEquals(listOf(false, true, true, true, false, false, false), sunday.map { it.trained })
        assertTrue(sunday[4].today)

        // On the week's first day the row starts today; on its last it ends today.
        assertTrue(Streak.week(emptyList(), today - 3, 1).first().today)
        assertTrue(Streak.week(emptyList(), today + 3, 1).last().today)
    }

    @Test
    fun theWeekRowMarksTheRestDaysTheStreakCarriedOver() {
        // Thursday. Monday trained, Tuesday off, Wednesday trained.
        val kept = Streak.week(days(3, 1), today, firstWeekday = 1)
        assertEquals(listOf(false, true, false, false, false, false, false), kept.map { it.rest })
        // Tuesday trained, Wednesday off, today not trained yet: yesterday is the rest day while today is open.
        val due = Streak.week(days(2), today, firstWeekday = 1)
        assertEquals(listOf(false, false, true, false, false, false, false), due.map { it.rest })
        // Today is never marked, and no day after it.
        assertTrue(Streak.week(days(1), today, firstWeekday = 1).none { it.rest })
        // Monday trained, then two days off: the run ended, nothing is marked.
        assertTrue(Streak.week(days(3), today, firstWeekday = 1).none { it.rest })
        // A rest day on the week's first day looks back into last week: Sunday trained, Monday off, Tuesday trained.
        assertTrue(Streak.week(days(4, 2), today, firstWeekday = 1).first().rest)
        // A trained day is never a rest day.
        assertTrue(Streak.week(run(6, 0), today, firstWeekday = 1).none { it.rest })
    }

    @Test
    fun aDayAfterTodayIsNotFilledInTheWeekRow() {
        val week = Streak.week(days(0, -1), today, firstWeekday = 1)
        assertTrue(week[3].trained)
        assertFalse(week[4].trained)
    }

    @Test
    fun markedDaysDoNotCount() {
        val noon = at(athens, 2026, 11, 12, 12, 0)
        val sessions = listOf(
            session(noon, manual = true),
            session(noon - DAY, repeat = true),
            session(noon - 2 * DAY, id = "custom:1"),
            session(noon - 3 * DAY, manual = true),
            session(noon - 4 * DAY),
        )
        assertEquals(setOf(today - 1, today - 2, today - 4), StreakDays.trained(sessions, athens))
        // Complete day today does not light the flame. The repeat and the athlete's own workout count, and the day only
        // marked complete before them is the one rest day, so the class four days ago is in the run too.
        assertEquals(Streak(3, 3, false, restToday = true), StreakDays.snapshot(sessions, noon, athens, Calendar.MONDAY).streak)
        assertFalse(Streak.counts(WorkoutSession("sg:l01:p0001", 0L, 0L, 0L)))
    }

    @Test
    fun daysTurnAtLocalMidnight() {
        // 23:50 and 00:10 are two days in Athens: a streak of two.
        val late = at(athens, 2026, 11, 11, 23, 50)
        val early = at(athens, 2026, 11, 12, 0, 10)
        val snapshot = StreakDays.snapshot(listOf(session(late), session(early)), early, athens, Calendar.MONDAY)
        assertEquals(Streak(2, 2, true), snapshot.streak)
        assertEquals(today, StreakDays.epochDay(early, athens))
        assertEquals(today - 1, StreakDays.epochDay(late, athens))
        // The same two instants fall on one evening in New York.
        assertEquals(StreakDays.epochDay(late, newYork), StreakDays.epochDay(early, newYork))
    }

    @Test
    fun localDaysMatchTheCalendarsAcrossDaylightSaving() {
        // October 25, 2026 has 25 hours in Athens and March 29 has 23; every hour stays on its date.
        for ((month, day) in listOf(10 to 24, 10 to 25, 10 to 26, 3 to 28, 3 to 29, 3 to 30)) {
            for (hour in listOf(0, 3, 4, 12, 23)) {
                val instant = at(athens, 2026, month, day, hour, 30)
                val date = LocalDate.of(2026, month, day)
                assertEquals("$date $hour:30", date.toEpochDay(), StreakDays.epochDay(instant, athens))
                assertEquals(date.year * 10_000 + month * 100 + day, HistoryCalendar.dayKey(instant, athens))
            }
        }
    }

    @Test
    fun theWeekFollowsThePhonesFirstDay() {
        val now = at(athens, 2026, 11, 12, 9, 0)
        val monday = StreakDays.snapshot(emptyList(), now, athens, Calendar.MONDAY).week
        val sunday = StreakDays.snapshot(emptyList(), now, athens, Calendar.SUNDAY).week
        val saturday = StreakDays.snapshot(emptyList(), now, athens, Calendar.SATURDAY).week
        assertEquals(DayOfWeek.MONDAY.value, Streak.weekday(monday.first().epochDay))
        assertEquals(DayOfWeek.SUNDAY.value, Streak.weekday(sunday.first().epochDay))
        assertEquals(DayOfWeek.SATURDAY.value, Streak.weekday(saturday.first().epochDay))
        for (calendarDay in Calendar.SUNDAY..Calendar.SATURDAY) {
            assertEquals(calendarDay, StreakDays.calendarDay(StreakDays.isoWeekday(calendarDay)))
        }
        assertEquals(Calendar.THURSDAY, StreakDays.calendarDay(Streak.weekday(today)))
    }

    @Test
    fun theNextDayStartsAtLocalMidnight() {
        assertEquals(at(athens, 2026, 11, 13, 0, 0), StreakDays.nextMidnight(at(athens, 2026, 11, 12, 9, 0), athens))
        assertEquals(at(athens, 2026, 11, 13, 0, 0), StreakDays.nextMidnight(at(athens, 2026, 11, 12, 23, 59), athens))
        // At midnight exactly, the next one is a day away.
        assertEquals(at(athens, 2026, 11, 13, 0, 0), StreakDays.nextMidnight(at(athens, 2026, 11, 12, 0, 0), athens))
        // The 25-hour and 23-hour days end at their own midnights.
        assertEquals(at(athens, 2026, 10, 26, 0, 0), StreakDays.nextMidnight(at(athens, 2026, 10, 25, 1, 0), athens))
        assertEquals(at(athens, 2026, 3, 30, 0, 0), StreakDays.nextMidnight(at(athens, 2026, 3, 29, 1, 0), athens))
        assertEquals(at(newYork, 2026, 11, 2, 0, 0), StreakDays.nextMidnight(at(newYork, 2026, 11, 1, 12, 0), newYork))
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
