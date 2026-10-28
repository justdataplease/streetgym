package com.justdataplease.streetgym.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionTapsTest {
    @Test
    fun countsDownFromTheFourthTapAndOpensOnTheTenth() {
        val taps = VersionTaps()
        val steps = List(VersionTaps.TAPS) { taps.tap() }
        assertEquals(List(3) { VersionTaps.Step.Quiet }, steps.take(3))
        assertEquals((6 downTo 1).map { VersionTaps.Step.Countdown(it) }, steps.subList(3, 9))
        assertEquals(VersionTaps.Step.Open, steps.last())
        // Once reached, every further tap opens the menu rather than counting from the start.
        assertEquals(VersionTaps.Step.Open, taps.tap())
    }
}
