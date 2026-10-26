package com.justdataplease.streetgym.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ParkRoutineTest {
    // 1960 to beyond 2200: the order is searched block by block, so every block it will meet must have one.
    private val days = -3_650L..84_000L
    private val size = ParkRoutine.all.size.toLong()

    @Test fun theThreeMovementsAreDifferentAndTheSkillFollowsItsStrengthMove() {
        assertEquals(21, ParkRoutine.all.size)
        for (routine in ParkRoutine.all) {
            assertTrue("$routine", routine.opener in ParkRoutine.OPENERS)
            assertTrue("$routine", routine.finale in ParkRoutine.FINALES)
            assertTrue("$routine", routine.middle in ParkRoutine.middlesFor(routine.finale))
            assertEquals("$routine", 3, routine.moves.toSet().size)
        }
    }

    @Test fun noMovementRepeatsTheDayBefore() {
        for (day in days) {
            val today = ParkRoutine.forEpochDay(day)
            val tomorrow = ParkRoutine.forEpochDay(day + 1)
            for (i in 0..2) assertNotEquals("day $day repeats", today.moves[i], tomorrow.moves[i])
        }
    }

    @Test fun everyBlockPlaysEachRoutineOnce() {
        for (block in days.first / size..days.last / size) {
            val seen = (0L until size).map { ParkRoutine.forEpochDay(block * size + it) }.toSet()
            assertEquals("block $block", ParkRoutine.all.toSet(), seen)
        }
    }

    @Test fun noRoutineComesBackWithinAWeekOrWaitsLongerThanTwoBlocks() {
        val last = HashMap<ParkRoutine, Long>()
        val gaps = ArrayList<Long>()
        for (day in days) {
            val routine = ParkRoutine.forEpochDay(day)
            last[routine]?.let { gaps += day - it }
            last[routine] = day
        }
        val longest = gaps.max()
        println("routines come back after ${gaps.min()} to $longest days")
        assertTrue("a routine came back after ${gaps.min()} days", gaps.min() >= 7)
        assertTrue("a routine waited $longest days", longest <= 2 * size - 1)
    }

    @Test fun theOrderChangesFromBlockToBlock() {
        val orders = (0L until 200L).map { block -> (0L until size).map { ParkRoutine.forEpochDay(block * size + it) } }.toSet()
        assertTrue("only ${orders.size} different orders in 200 blocks", orders.size >= 190)
        // On average a routine comes back after a block; check it is not pinned to the same weekday.
        val weekdays = days.filter { ParkRoutine.forEpochDay(it) == ParkRoutine.all[0] }.map { it.mod(7L) }.toSet()
        assertEquals(7, weekdays.size)
    }

    @Test fun aLocalDayKeepsItsRoutineUntilMidnight() {
        val zone = TimeZone.getTimeZone("Europe/Athens")
        // 2026-03-29 is 23 hours long in Athens (summer time starts), 2026-10-25 is 25.
        for (date in listOf(Triple(2026, Calendar.MARCH, 29), Triple(2026, Calendar.OCTOBER, 25), Triple(2026, Calendar.JUNE, 1))) {
            val calendar = Calendar.getInstance(zone).apply {
                clear()
                set(date.first, date.second, date.third, 0, 0, 0)
            }
            val midnight = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val nextMidnight = calendar.timeInMillis
            val day = ParkRoutine.localEpochDay(midnight, zone)
            assertEquals(day, ParkRoutine.localEpochDay(nextMidnight - 1, zone))
            assertEquals(day + 1, ParkRoutine.localEpochDay(nextMidnight, zone))
            assertEquals(day - 1, ParkRoutine.localEpochDay(midnight - 1, zone))
        }
    }

    @Test fun epochDayZeroIsTheFirstOfJanuary1970InUtc() {
        assertEquals(0L, ParkRoutine.localEpochDay(0L, TimeZone.getTimeZone("UTC")))
        assertEquals(-1L, ParkRoutine.localEpochDay(-1L, TimeZone.getTimeZone("UTC")))
        // Athens is ahead of UTC, so its 1 January began two hours before the epoch.
        assertEquals(0L, ParkRoutine.localEpochDay(-7_200_000L, TimeZone.getTimeZone("Europe/Athens")))
    }
}
