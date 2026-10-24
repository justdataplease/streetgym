package com.justdataplease.streetgym.timer

import com.justdataplease.streetgym.ads.AdBreakClock
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimingStateTest {
    @Test fun countdownPauseResumeKeepsRemainingTime() {
        val paused = Countdown(60_000).start(10_000).pause(25_000)
        assertEquals(45_000L, paused.remaining(9_000_000))
        val resumed = paused.start(100_000)
        assertEquals(30_000L, resumed.remaining(115_000))
        assertEquals(0L, resumed.remaining(999_000))
    }
    @Test fun duplicateStartDoesNotExtendRunningTimer() {
        val running = Countdown(30_000).start(1000)
        assertEquals(running, running.start(20_000))
    }
    @Test fun finishedTimerRestartsAtOriginalDuration() {
        val finished = Countdown(20_000).start(0).finish()
        assertEquals(0L, finished.left)
        assertEquals(20_000L, finished.start(100_000).remaining(100_000))
        assertFalse(Countdown(0).start(0).running)
    }
    @Test fun resetStopsTheTimer() {
        val reset = Countdown(5000).start(100).reset()
        assertFalse(reset.running); assertEquals(5000L, reset.remaining(10_000))
    }
    @Test fun stopwatchPauseAndResumePreserveLapsAndElapsed() {
        val paused = Stopwatch().toggle(1000).lap(3500).toggle(6000)
        assertEquals(listOf(2500L), paused.laps)
        assertEquals(5000L, paused.elapsed(90000))
        val resumed = paused.toggle(100000).lap(102000)
        assertEquals(listOf(2500L,7000L), resumed.laps)
        assertEquals(7000L, resumed.elapsed(102000))
    }
    @Test fun stopwatchLapsAreBoundedAndOnlySavedWhenRunning() {
        assertEquals(Stopwatch(), Stopwatch().lap(5000))
        var watch = Stopwatch().toggle(0)
        repeat(105) { watch = watch.lap(it * 100L) }
        assertEquals(100, watch.laps.size); assertEquals(10400L, watch.laps.last())
    }
    @Test fun countdownDisplayRoundsUpAndStopwatchDoesNot() {
        assertEquals("00:01", clockText(1, true))
        assertEquals("00:00", clockText(999))
        assertEquals("1:00:00", clockText(3_600_000))
        assertEquals("00:00", clockText(-400))
    }
    private val utc = TimeZone.getTimeZone("UTC")
    private fun date(day: Int, hour: Int, minute: Int) = Calendar.getInstance(utc).apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, minute)
    }.timeInMillis
    @Test fun alarmUsesTodayIfTimeIsAheadOtherwiseTomorrow() {
        assertEquals(date(24,9,30), nextAlarm(9,30,date(24,8,0),utc))
        assertEquals(date(25,9,30), nextAlarm(9,30,date(24,9,30),utc))
    }
    @Test fun nextDayAlarmUsesLocalDateAcrossDst() {
        val zone = TimeZone.getTimeZone("Europe/Athens")
        val now = Calendar.getInstance(zone).apply { clear(); set(2026,Calendar.OCTOBER,24,12,0) }.timeInMillis
        val next = nextAlarm(9,0,now,zone)
        val result = Calendar.getInstance(zone).apply { timeInMillis = next }
        assertEquals(25,result.get(Calendar.DAY_OF_MONTH)); assertEquals(9,result.get(Calendar.HOUR_OF_DAY))
        assertEquals(22 * 3_600_000L,next-now)
    }
    @Test fun adIntervalCountsOnlyForegroundTime() {
        val clock = AdBreakClock(); clock.resume(0); clock.pause(300_000)
        assertFalse(clock.due(9_000_000))
        clock.resume(10_000_000)
        assertFalse(clock.due(10_299_999)); assertTrue(clock.due(10_300_000))
        clock.shown(10_300_000); assertFalse(clock.due(10_300_001))
    }
    @Test fun missedAdBreakDoesNotQueueSeveralAds() {
        val clock = AdBreakClock(); clock.resume(0)
        assertTrue(clock.due(1_800_000)); clock.shown(1_800_000)
        assertFalse(clock.due(1_800_000)); assertTrue(clock.due(2_400_000))
    }
}
