package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AlarmAction
import com.justdataplease.streetgym.app.data.Alerts
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.Countdown
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.Stopwatch
import com.justdataplease.streetgym.app.data.Timing
import com.justdataplease.streetgym.app.data.TimingClock
import com.justdataplease.streetgym.app.data.TimingStore
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.data.nextAlarm
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.alarmClockText
import com.justdataplease.streetgym.app.ui.clockText
import com.justdataplease.streetgym.app.ui.stopwatchText
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Timer tab (Android: TimingPanel, TimingState, TimingStore, TimingAlerts and timingTick) on a stepped clock: the
 * timer, the stopwatch and the alarm, their state kept across tabs and after the app is read again (and across a restart
 * of the phone), Android's stored JSON, the alarm's day boundaries against Android's java.util.Calendar code, and every
 * alert through a recording stand-in for the phone.
 */
@OptIn(ExperimentalTestApi::class)
class TimerTest {
    /** The elapsed clock, the wall clock and the phone's start, stepped by the tests. */
    private class Clock(var elapsed: Long = 5_000_000L, var wall: Long = WED_NOON, var boot: Long = 1L) : TimingClock {
        override fun elapsed(): Long = elapsed
        override fun wall(): Long = wall
        override fun boot(): Long = boot
        fun step(millis: Long) {
            elapsed += millis
            wall += millis
        }
    }

    /** Records what the Timer asks of the phone. */
    private class Phone(var status: Alerts = Alerts.ALLOWED, val allow: Boolean = true) : WorkoutDevice {
        val timers = mutableListOf<Long?>()
        val alarms = mutableListOf<LocalDateTime?>()
        var cleared = 0
        var tones = 0
        var asked = 0
        var settings = 0
        override fun timerAlert(inMillis: Long?) { timers += inMillis }
        override fun alarmAlert(at: LocalDateTime?) { alarms += at }
        override fun clearTimingAlerts() { cleared++ }
        override fun timingTone() { tones++ }
        override fun alerts(result: (Alerts) -> Unit) = result(status)
        override fun askAlerts(done: (Boolean) -> Unit) {
            asked++
            status = if (allow) Alerts.ALLOWED else Alerts.DENIED
            done(allow)
        }
        override fun openAlertSettings() { settings++ }
    }

    private fun timing(store: MemoryStore, clock: Clock, phone: Phone = Phone(), zone: TimeZone = TimeZone.UTC, hour24: Boolean = true) =
        Timing(TimingStore(store, clock), phone, { zone }, hour24)

    private fun model(store: MemoryStore, timing: Timing, phone: Phone): AppModel {
        AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), now = { WED_NOON }, zone = TimeZone.UTC, device = phone,
            settings = AppSettings(store), timing = timing)
    }

    @Test
    fun theTimerCountsDownPausesResumesAndRings() {
        val clock = Clock()
        val phone = Phone()
        val timer = timing(MemoryStore(), clock, phone)
        assertEquals(60_000L, timer.state.countdown.total)
        timer.timerPrimary()
        assertTrue(timer.state.countdown.running)
        assertEquals(60_000L, phone.timers.last())
        clock.step(25_400)
        timer.tick()
        assertEquals(34_600L, timer.state.countdown.remaining(timer.now))
        assertEquals("00:35", clockText(timer.state.countdown.remaining(timer.now), roundUp = true))
        // Pause keeps what is left; the alert goes. Resume runs on from there.
        timer.timerPrimary()
        assertFalse(timer.state.countdown.running)
        assertEquals(34_600L, timer.state.countdown.left)
        assertNull(phone.timers.last())
        clock.step(60_000)
        timer.timerPrimary()
        assertEquals(34_600L, phone.timers.last())
        // It runs out: stopped at zero, ringing, until Stop, which takes its alert away.
        clock.step(34_600)
        assertTrue(timer.checkDue().timerRinging)
        assertEquals(0L, timer.state.countdown.left)
        assertFalse(timer.state.countdown.running)
        timer.stop()
        assertFalse(timer.state.ringing)
        assertEquals(1, phone.cleared)
        // Reset: the whole minute again.
        timer.timerReset()
        assertEquals(Countdown(60_000L), timer.state.countdown)
        // Presets, and the picker's duration; none is refused ("Choose a duration").
        timer.preset(180_000L)
        assertEquals(180_000L, timer.state.countdown.total)
        assertFalse(timer.setDuration(0L))
        assertTrue(timer.setDuration((1 * 3_600L + 2 * 60L + 3L) * 1_000L))
        assertEquals(3_723_000L, timer.state.countdown.total)
        assertEquals("1:02:03", clockText(timer.state.countdown.total, roundUp = true))
    }

    @Test
    fun theStopwatchPausesLapsAndKeepsAHundredLaps() {
        val clock = Clock()
        val watch = timing(MemoryStore(), clock)
        watch.stopwatchPrimary()
        clock.step(1_234)
        watch.stopwatchSecondary()
        clock.step(10_000)
        watch.stopwatchSecondary()
        assertEquals(listOf(1_234L, 11_234L), watch.state.stopwatch.laps)
        watch.tick()
        assertEquals("00:11.23", stopwatchText(watch.state.stopwatch.elapsed(watch.now)))
        watch.stopwatchPrimary()
        clock.step(5_000)
        watch.tick()
        assertEquals(11_234L, watch.state.stopwatch.elapsed(watch.now))
        // Resumed, a lap every 10 ms: the last 100 are kept. Paused, the second button resets.
        watch.stopwatchPrimary()
        repeat(120) {
            clock.step(10)
            watch.stopwatchSecondary()
        }
        assertEquals(Stopwatch.MAX_LAPS, watch.state.stopwatch.laps.size)
        assertEquals(11_234L + 1_200L, watch.state.stopwatch.laps.last())
        watch.stopwatchPrimary()
        watch.stopwatchSecondary()
        assertEquals(Stopwatch(), watch.state.stopwatch)
    }

    @Test
    fun theAlarmIsSetForTheNextTimeAsksForAlertsAndRings() {
        val clock = Clock()
        val phone = Phone(status = Alerts.NOT_ASKED)
        val athens = TimeZone.of("Europe/Athens")
        val store = MemoryStore()
        val alarm = timing(store, clock, phone, athens)
        alarm.refreshAlerts()
        // Alerts off: the button asks for them first, then sets the alarm.
        assertEquals(AlarmAction.ENABLE_ALERTS, alarm.alarmAction())
        alarm.alarmPrimary()
        assertEquals(1, phone.asked)
        assertTrue(alarm.alertsOn)
        alarm.setAlarmTime(7, 30)
        assertEquals(0L, alarm.state.alarmAt)
        alarm.alarmPrimary()
        // Wednesday noon UTC is 14:00 in Athens: tomorrow's 7:30 there, 05:30 UTC.
        assertEquals(WED_NOON + 17 * HOUR + 30 * MINUTE, alarm.state.alarmAt)
        assertEquals(LocalDateTime(2026, 11, 19, 7, 30), phone.alarms.last())
        assertEquals(AlarmAction.TURN_OFF, alarm.alarmAction())
        // A new time moves a set alarm.
        alarm.setAlarmTime(15, 0)
        assertEquals(WED_NOON + 1 * HOUR, alarm.state.alarmAt)
        // The time zone changes: the alarm keeps to the wall clock where the phone now is.
        val moved = timing(store, clock, phone, TimeZone.of("Europe/London"))
        moved.timeChanged()
        assertEquals(WED_NOON + 3 * HOUR, moved.state.alarmAt)
        // It rings, and turns off (one time).
        clock.wall = moved.state.alarmAt
        assertTrue(moved.checkDue().alarmRinging)
        assertEquals(0L, moved.state.alarmAt)
        assertNull(phone.alarms.last())
        moved.stop()
        assertFalse(moved.state.alarmRinging)
        // Turned off by the athlete in the phone's settings: Enable alerts opens them.
        phone.status = Alerts.DENIED
        moved.refreshAlerts()
        moved.enableAlerts()
        assertEquals(1, phone.settings)
    }

    @Test
    fun theClocksAreKeptWithAndroidsNamesAndReadAgainAfterARestart() {
        val store = MemoryStore()
        val clock = Clock()
        val first = timing(store, clock)
        first.selectTab(1)
        first.timerPrimary()
        first.stopwatchPrimary()
        clock.step(2_000)
        first.stopwatchSecondary()
        first.setAlarmTime(6, 45)
        val json = JSONObject(store.get("streetgym_timing.state")!!)
        assertEquals(setOf("boot", "total", "running", "end", "left", "wallEnd", "watchRunning", "watchStart", "banked",
            "watchWallStart", "laps", "alarmAt", "alarmHour", "alarmMinute", "timerRinging", "alarmRinging", "tab"), json.keySet())
        assertEquals("2000", json.getString("laps"))
        assertEquals(1, json.getInt("tab"))

        // The app read again: everything as it was, still running.
        clock.step(3_000)
        val again = timing(store, clock)
        again.tick()
        assertEquals(1, again.state.tab)
        assertEquals(55_000L, again.state.countdown.remaining(again.now))
        assertEquals(5_000L, again.state.stopwatch.elapsed(again.now))
        assertEquals(6 to 45, again.state.alarmHour to again.state.alarmMinute)

        // A restart of the phone: the elapsed clock starts again, the clocks carry on by the wall clock.
        clock.boot = 2L
        clock.elapsed = 1_000L
        clock.wall += 10_000L
        val restarted = timing(store, clock)
        restarted.tick()
        assertEquals(45_000L, restarted.state.countdown.remaining(restarted.now))
        assertEquals(15_000L, restarted.state.stopwatch.elapsed(restarted.now))
        assertEquals(2L, JSONObject(store.get(TimingStore.KEY)!!).getLong("boot"))
    }

    @Test
    fun theAlarmsDayMatchesAndroidsCalendarInEveryZone() {
        val zones = listOf("UTC", "Europe/Athens", "America/New_York", "Australia/Lord_Howe", "Asia/Kolkata", "Pacific/Chatham",
            "America/Sao_Paulo", "Europe/London", "America/St_Johns", "Asia/Tehran")
        val times = listOf(0 to 0, 1 to 0, 1 to 30, 2 to 0, 2 to 15, 2 to 30, 3 to 0, 7 to 0, 12 to 0, 23 to 59)
        var checked = 0
        for (id in zones) {
            val java = java.util.TimeZone.getTimeZone(id)
            val zone = TimeZone.of(id)
            fun check(now: Long) {
                for ((hour, minute) in times) {
                    assertEquals(androidNextAlarm(hour, minute, now, java), nextAlarm(hour, minute, now, zone),
                        "$id ${hour}:$minute at $now")
                    checked++
                }
            }
            // Two years, at times of day that drift round the clock.
            var now = 20_454L * DAY + 17 * MINUTE
            while (now < 21_184L * DAY) {
                check(now)
                now += 3 * HOUR + 47 * MINUTE
            }
            // Every day the clocks change, and the day before, every 20 minutes.
            for (day in 20_454L until 21_184L) {
                if (java.getOffset(day * DAY) == java.getOffset((day + 1) * DAY)) continue
                var at = (day - 1) * DAY
                while (at < (day + 2) * DAY) {
                    check(at)
                    at += 20 * MINUTE
                }
            }
        }
        assertTrue(checked > 300_000)
    }

    @Test
    fun theTimeTexts() {
        assertEquals("07:05", alarmClockText(7, 5, hour24 = true))
        assertEquals("7:05 AM", alarmClockText(7, 5, hour24 = false))
        assertEquals("12:00 AM", alarmClockText(0, 0, hour24 = false))
        assertEquals("12:30 PM", alarmClockText(12, 30, hour24 = false))
        assertEquals("11:59 PM", alarmClockText(23, 59, hour24 = false))
        assertEquals("00:00.00", stopwatchText(0))
        assertEquals("1:00:00.99", stopwatchText(3_600_999))
        val timer = timing(MemoryStore(), Clock())
        assertEquals("Wed, 18 Nov", com.justdataplease.streetgym.app.ui.alarmDay(WED_NOON, timer))
    }

    @Test
    fun theTimerTabByTapsKeepsItsStateAcrossTabs() = runComposeUiTest {
        val store = MemoryStore()
        val clock = Clock()
        val phone = Phone(status = Alerts.NOT_ASKED)
        val model = model(store, timing(store, clock, phone), phone)
        setContent { StreetGymApp(model, PaddingValues()) }

        onAllNodesWithContentDescription("Timer").onLast().performClick()
        onNodeWithText("Tap the time to change it").assertExists()
        onNodeWithText("Background alerts are off. Enable alerts").assertExists()
        readout("Change timer duration", "01:00").assertExists()
        onNodeWithText("START").performClick()
        onNodeWithText("Counting down").assertExists()
        onNodeWithText("PAUSE").assertExists()
        assertEquals(60_000L, phone.timers.last())

        // Today and back: still counting down, 20 seconds on.
        onNodeWithContentDescription("Today").performClick()
        clock.step(20_000)
        onAllNodesWithContentDescription("Timer").onLast().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(200)
        readout("Change timer duration", "00:40").assertExists()

        // The stopwatch and the alarm, kept as the tab picked.
        onAllNodesWithContentDescription("Stopwatch").onFirst().performClick()
        onNodeWithText("Ready").assertExists()
        onNodeWithText("START").performClick()
        clock.step(1_500)
        onNodeWithText("LAP").performClick()
        onNodeWithText("Lap 1").assertExists()
        onNodeWithText("Background alerts are off. Enable alerts").assertDoesNotExist()
        onAllNodesWithContentDescription("Alarm").onFirst().performClick().assertIsSelected()
        assertEquals(2, JSONObject(store.get(TimingStore.KEY)!!).getInt("tab"))
        readout("Change alarm time", "07:00").assertExists()
        onNodeWithText("ENABLE ALERTS").performClick()
        assertEquals(1, phone.asked)
        onNodeWithText("SET ALARM").performClick()
        onNodeWithText("Thu, 19 Nov · One time").assertExists()
        onNodeWithText("TURN OFF").assertExists()

        // The time's picker: Android's clock face, Cancel and OK.
        onNodeWithContentDescription("Change alarm time").performClick()
        onNodeWithText("OK").performClick()

        // The timer runs out on another tab: "Time's up" over it, silent with alerts on; Stop.
        onNodeWithContentDescription("Today").performClick()
        clock.step(40_000)
        mainClock.advanceTimeBy(1_100)
        onNodeWithText("Time's up").assertExists()
        assertEquals(0, phone.tones)
        onNodeWithText("STOP").performClick()
        onNodeWithText("Time's up").assertDoesNotExist()
        assertEquals(1, phone.cleared)
    }

    @Test
    fun withAlertsOffTheAppBeepsAndATapOnTheAlertOpensTheTimer() = runComposeUiTest {
        val store = MemoryStore()
        val clock = Clock()
        val phone = Phone(status = Alerts.DENIED)
        val timing = timing(store, clock, phone)
        val model = model(store, timing, phone)
        timing.refreshAlerts()
        timing.timerPrimary()
        setContent { StreetGymApp(model, PaddingValues()) }
        clock.step(61_000)
        mainClock.advanceTimeBy(1_100)
        onNodeWithText("Time's up").assertExists()
        assertEquals(1, phone.tones)
        onNodeWithText("STOP").performClick()
        timing.openRequested = true
        waitForIdle()
        onNodeWithText("Time’s up").assertExists()
        readout("Change timer duration", "00:00").assertExists()
        onAllNodesWithContentDescription("Timer").onFirst().assertIsSelected()
        // The duration's picker: Cancel and Set, none refused.
        onNodeWithContentDescription("Change timer duration").performClick()
        onNodeWithText("Hours").assertExists()
        onNodeWithText("SET").performClick()
        assertEquals(60_000L, timing.state.countdown.total)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.readout(description: String, time: String) =
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(description)) and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, time))

    /** Android's nextAlarm, word for word (timer/TimingState.kt). */
    private fun androidNextAlarm(hour: Int, minute: Int, now: Long, zone: java.util.TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        /** Wednesday 18 November 2026, 12:00 UTC. */
        const val WED_NOON = 20_775L * DAY + 12 * HOUR
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
