package com.justdataplease.streetgym.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTapsTest {
    @Test
    fun countsDownFromTheFourthTapAndAsksForThePasscodeOnTheTenth() {
        val taps = VersionTaps()
        val steps = List(VersionTaps.TAPS) { taps.tap() }
        assertEquals(List(3) { VersionTaps.Step.Quiet }, steps.take(3))
        assertEquals((6 downTo 1).map { VersionTaps.Step.Countdown(it) }, steps.subList(3, 9))
        assertEquals(VersionTaps.Step.Passcode, steps.last())
        // After a wrong or cancelled passcode, the next tap asks again rather than counting from the start.
        assertEquals(VersionTaps.Step.Passcode, taps.tap())
    }

    @Test
    fun acceptsOnlyThePasscode() {
        assertTrue(VersionTaps.accepts("0002"))
        assertTrue(VersionTaps.accepts(" 0002 "))
        assertFalse(VersionTaps.accepts("2"))
        assertFalse(VersionTaps.accepts("0003"))
        assertFalse(VersionTaps.accepts(""))
    }
}
