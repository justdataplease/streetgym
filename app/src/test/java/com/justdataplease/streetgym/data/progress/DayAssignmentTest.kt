package com.justdataplease.streetgym.data.progress

import org.junit.Assert.*
import org.junit.Test

class DayAssignmentTest {
    private data class Day(val id: String, val level: Int)

    // three levels of four days: 1-1 .. 3-4
    private val days = (1..3).flatMap { level -> (1..4).map { Day("$level-$it", level) } }
    private fun ids(vararg level: Int) = days.filter { it.level in level }.map { it.id }.toSet()
    private fun next(completed: Set<String>, replay: LevelReplay?) =
        DayAssignment.next(days, completed, replay, Day::id, Day::level)?.id
    private fun replaying(completed: Set<String>, replay: LevelReplay?) =
        DayAssignment.replaying(days, completed, replay, Day::id, Day::level)

    @Test fun withoutAReplayTheFirstDayNeverCompletedIsNext() {
        assertEquals("1-1", next(emptySet(), null))
        assertEquals("3-2", next(ids(1, 2) + "3-1", null))
        assertNull(next(ids(1, 2, 3), null))
    }

    @Test fun aReplayRestartsAtTheFirstDayOfItsLevel() {
        val completed = ids(1, 2) + "3-1"
        assertEquals("1-1", next(completed, LevelReplay(1)))
        assertEquals("2-1", next(completed, LevelReplay(2)))
        assertTrue(replaying(completed, LevelReplay(2)))
    }

    @Test fun aReplayMovesForwardThroughTheDaysRepeated() {
        val completed = ids(1, 2) + "3-1"
        assertEquals("2-3", next(completed, LevelReplay(2, setOf("2-1", "2-2"))))
        // past the replayed level it continues into the next one, as the athlete rebuilds
        assertEquals("3-1", next(completed, LevelReplay(2, ids(2))))
    }

    @Test fun aReplayEndsAtTheFurthestDay() {
        val completed = ids(1, 2) + "3-1"
        val caughtUp = LevelReplay(2, ids(2) + "3-1")
        assertEquals("3-2", next(completed, caughtUp))
        assertFalse(replaying(completed, caughtUp))
    }

    @Test fun aReplayAfterTheWholeSequenceRunsToItsEnd() {
        val completed = ids(1, 2, 3)
        assertEquals("3-1", next(completed, LevelReplay(3)))
        assertNull(next(completed, LevelReplay(3, ids(3))))
        assertFalse(replaying(completed, LevelReplay(3, ids(3))))
    }

    @Test fun noReplayIsNotReplaying() {
        assertFalse(replaying(ids(1), null))
    }

    @Test fun aDaySkippedOnTheFirstPassIsStillReachedAfterTheReplay() {
        // 2-3 was never completed (an out-of-order completion): the replay stops there, as the plain sequence would
        val completed = ids(1) + setOf("2-1", "2-2", "2-4")
        assertEquals("1-1", next(completed, LevelReplay(1)))
        assertEquals("2-3", next(completed, LevelReplay(1, ids(1) + "2-1" + "2-2")))
    }
}
