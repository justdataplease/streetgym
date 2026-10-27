package com.justdataplease.streetgym.data.progress

import org.junit.Assert.*
import org.junit.Test

class ExamLogTest {
    private val first = ExamResult("sg:l04:p0013", 1_000L, listOf("pullup", "straight_bar_dip"), listOf("tuck_planche"))
    private val retake = ExamResult("sg:l04:p0013", 5_000L, listOf("pullup", "straight_bar_dip", "tuck_planche"), emptyList())

    @Test fun anExamPassesWhenNothingWasMissed() {
        assertFalse(first.passed)
        assertTrue(retake.passed)
    }

    @Test fun theLatestAttemptCounts() {
        val log = ExamLog().record(retake).record(first)
        assertEquals(retake, log.latest("sg:l04:p0013"))
        assertNull(log.latest("sg:l04:p0034"))
        assertEquals(listOf(first, retake), log.results)
    }

    @Test fun encodingRoundTrips() {
        val log = ExamLog().record(first).record(retake)
        assertEquals(log, ExamLog.decode(log.encode()))
        assertEquals(ExamLog(), ExamLog.decode(null))
        assertEquals(ExamLog(), ExamLog.decode("not json"))
    }

    @Test fun aMergeKeepsEveryAttemptOnce() {
        val phone = ExamLog().record(first)
        val cloud = ExamLog().record(first).record(retake)
        assertEquals(listOf(first, retake), phone.merge(cloud).results)
        assertEquals(phone.merge(cloud), cloud.merge(phone))
    }
}
