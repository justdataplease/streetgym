package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * The Timer tab's clocks (android-streetgym, timer/TimingState.kt, TimingStore.kt and TimingAlerts.kt), with the same
 * names, numbers and stored JSON. Android's day boundaries use java.util.Calendar; here they are kotlinx-datetime,
 * pinned to Android's code by TimerTest.
 */

/** Adapted from Just a Toolbox: elapsed time is independent of wall-clock changes. */
data class Countdown(val total: Long = 60_000L, val running: Boolean = false, val endsAt: Long = 0L, val left: Long = total) {
    fun remaining(now: Long) = if (running) (endsAt - now).coerceAtLeast(0L) else left
    fun start(now: Long): Countdown = if (running || total <= 0) this else {
        val duration = left.takeIf { it > 0 } ?: total
        copy(running = true, endsAt = now + duration, left = duration)
    }
    fun pause(now: Long) = if (running) copy(running = false, left = remaining(now)) else this
    fun reset() = Countdown(total)
    fun finish() = copy(running = false, left = 0L)
}

/** Toolbox stopwatch behaviour, using elapsed milliseconds and bounded lap storage. */
data class Stopwatch(val running: Boolean = false, val startedAt: Long = 0L, val banked: Long = 0L, val laps: List<Long> = emptyList()) {
    fun elapsed(now: Long) = banked + if (running) (now - startedAt).coerceAtLeast(0L) else 0L
    fun toggle(now: Long) = if (running) copy(running = false, banked = elapsed(now)) else copy(running = true, startedAt = now)
    fun lap(now: Long) = if (running) copy(laps = (laps + elapsed(now)).takeLast(MAX_LAPS)) else this

    companion object {
        const val MAX_LAPS = 100
    }
}

/** The three clocks and which one shows: [tab] 0 Timer, 1 Stopwatch, 2 Alarm. [alarmAt] is wall time, 0 when off. */
data class TimingState(
    val countdown: Countdown = Countdown(),
    val stopwatch: Stopwatch = Stopwatch(),
    val alarmAt: Long = 0L,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val timerRinging: Boolean = false,
    val alarmRinging: Boolean = false,
    val tab: Int = 0,
) {
    val ringing: Boolean get() = timerRinging || alarmRinging
}

/**
 * The next [hour]:[minute] on the wall clock of [zone] after [now] (Android: nextAlarm, on java.util.Calendar): today's,
 * or tomorrow's once today's has passed. Around a change of the clocks the instants are Calendar's: today's time is
 * resolved as its lenient fields resolve it ([wallTime]), and a day is added as GregorianCalendar.add(DAY_OF_YEAR) adds
 * it ([nextDay]).
 */
fun nextAlarm(hour: Int, minute: Int, now: Long, zone: TimeZone): Long {
    require(hour in 0..23 && minute in 0..59)
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    val first = wallTime(LocalDateTime(today, LocalTime(hour, minute)), zone)
    return if (first > now) first else nextDay(first, zone)
}

/**
 * [at] plus one day as GregorianCalendar.add(DAY_OF_YEAR, 1) does it: the same time of day on the next date at [at]'s
 * offset, moved by the change of offset unless that moves it off the date. A time the clocks skip lands before the gap.
 */
internal fun nextDay(at: Long, zone: TimeZone): Long {
    val local = Instant.fromEpochMilliseconds(at).toLocalDateTime(zone)
    val offset = zone.offsetAt(Instant.fromEpochMilliseconds(at)).totalSeconds * 1_000L
    val date = local.date.plus(1, DateTimeUnit.DAY)
    val time = date.toEpochDays() * DAY_MILLIS + local.time.toMillisecondOfDay() - offset
    val change = offset - zone.offsetAt(Instant.fromEpochMilliseconds(time)).totalSeconds * 1_000L
    if (change == 0L) return time
    val moved = time + change
    return if (Instant.fromEpochMilliseconds(moved).toLocalDateTime(zone).date == date) moved else time
}

private const val DAY_MILLIS = 86_400_000L

/**
 * [local] as an instant in [zone], as java.util.GregorianCalendar resolves it: in a gap the time moves on by the gap's
 * length; a time that happens twice is its later, standard-time instant.
 */
internal fun wallTime(local: LocalDateTime, zone: TimeZone): Long {
    val earlier = local.toInstant(zone)
    // A repeated hour: the same wall time the overlap's length later, if it reads the same. Three hours on is past the
    // end of any overlap and before the zone's next change.
    val offset = zone.offsetAt(earlier)
    val laterOffset = zone.offsetAt(Instant.fromEpochMilliseconds(earlier.toEpochMilliseconds() + PROBE_MILLIS))
    if (laterOffset.totalSeconds < offset.totalSeconds) {
        val shifted = Instant.fromEpochMilliseconds(earlier.toEpochMilliseconds() + (offset.totalSeconds - laterOffset.totalSeconds) * 1_000L)
        if (shifted.toLocalDateTime(zone) == local) return shifted.toEpochMilliseconds()
    }
    return earlier.toEpochMilliseconds()
}

private const val PROBE_MILLIS = 3 * 3_600_000L

/**
 * The clocks the Timer runs on (Android: SystemClock.elapsedRealtime, System.currentTimeMillis and the boot count).
 * [elapsed] keeps counting while the phone sleeps and ignores changes to the wall clock; [boot] tells one start of the
 * phone from the next, as [elapsed] starts again with each.
 */
interface TimingClock {
    fun elapsed(): Long
    fun wall(): Long
    fun boot(): Long
}

/** The wall clock for all three: previews, the desktop window and tests. */
class WallClock(private val now: () -> Long) : TimingClock {
    override fun elapsed(): Long = now()
    override fun wall(): Long = now()
    override fun boot(): Long = 0L
}

/**
 * The clocks kept on the phone, apart from the workout's progress (Android: TimingStore, preferences "streetgym_timing",
 * key "state"), with Android's JSON names. Running clocks carry across a restart of the phone: their wall-clock ends are
 * kept beside the elapsed ones.
 */
class TimingStore(private val store: KeyValueStore, val clock: TimingClock) {
    // The last state read and the text and phone start it came from: the Timer reads it ten times a second.
    private var cache: Triple<String?, Long, TimingState>? = null

    fun load(): TimingState {
        val raw = store.get(KEY) ?: return TimingState()
        val boot = clock.boot()
        cache?.let { (text, start, state) -> if (text == raw && start == boot) return state }
        val state = parse(raw, boot)
        // After a restart of the phone [parse] saved the clocks again, and with them its own cache.
        if (store.get(KEY) == raw) cache = Triple(raw, boot, state)
        return state
    }

    private fun parse(raw: String, boot: Long): TimingState =
        runCatching {
            val o = JSONObject(raw)
            val elapsed = clock.elapsed()
            val wall = clock.wall()
            val rebooted = o.optLong("boot", -1L) != boot
            var timer = Countdown(o.getLong("total"), o.getBoolean("running"), o.getLong("end"), o.getLong("left"))
            var watch = Stopwatch(o.getBoolean("watchRunning"), o.getLong("watchStart"), o.getLong("banked"),
                o.optString("laps").split(',').mapNotNull { it.toLongOrNull() }.filter { it >= 0 }.takeLast(Stopwatch.MAX_LAPS))
            if (rebooted) {
                if (timer.running) timer = timer.copy(endsAt = elapsed + (o.getLong("wallEnd") - wall).coerceAtLeast(0))
                if (watch.running) watch = watch.copy(startedAt = elapsed,
                    banked = watch.banked + (wall - o.getLong("watchWallStart")).coerceAtLeast(0))
            }
            TimingState(timer, watch, o.optLong("alarmAt"), o.optInt("alarmHour", 7), o.optInt("alarmMinute"),
                o.optBoolean("timerRinging"), o.optBoolean("alarmRinging"), o.optInt("tab").coerceIn(0, 2))
                .also { if (rebooted) save(it) }
        }.getOrDefault(TimingState())

    fun save(state: TimingState) {
        val elapsed = clock.elapsed()
        val wall = clock.wall()
        val c = state.countdown
        val w = state.stopwatch
        val o = JSONObject().put("boot", clock.boot()).put("total", c.total).put("running", c.running)
            .put("end", c.endsAt).put("left", c.left).put("wallEnd", wall + c.endsAt - elapsed)
            .put("watchRunning", w.running).put("watchStart", w.startedAt).put("banked", w.banked)
            .put("watchWallStart", wall - (elapsed - w.startedAt)).put("laps", w.laps.joinToString(","))
            .put("alarmAt", state.alarmAt).put("alarmHour", state.alarmHour).put("alarmMinute", state.alarmMinute)
            .put("timerRinging", state.timerRinging).put("alarmRinging", state.alarmRinging).put("tab", state.tab)
        val raw = o.toString()
        store.set(KEY, raw)
        cache = Triple(raw, clock.boot(), state)
    }

    companion object {
        const val KEY = "streetgym_timing.state"
    }
}

/** Whether the phone shows the app's alerts: allowed, never asked, or turned off by the athlete. */
enum class Alerts { ALLOWED, NOT_ASKED, DENIED }

/** What the alarm's primary button does now (Android: TimingPanel.primaryAction on the Alarm). */
enum class AlarmAction { SET, TURN_OFF, ENABLE_ALERTS }

/**
 * The Timer tab and its alerts (Android: TimingPanel's actions, TimingAlerts and HomeActivity.timingTick), over
 * [store]. Every change is saved at once, so the clocks run on while another tab shows and after the app is closed, and
 * the phone's alerts are scheduled again ([WorkoutDevice.timerAlert], [WorkoutDevice.alarmAlert]): the end of the timer
 * and the alarm ring as alerts even with the app closed, and their time follows a change of the clock or the time zone
 * ([timeChanged]). [zone] is the phone's zone now; [hour24]: the phone shows times on a 24-hour clock.
 */
@Stable
class Timing(
    private val store: TimingStore,
    private val device: WorkoutDevice = WorkoutDevice.None,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    val hour24: Boolean = true,
) {
    private val clock: TimingClock get() = store.clock

    /** The clocks as saved. */
    var state by mutableStateOf(store.load())
        private set

    /** The elapsed clock when the Timer was last drawn: it moves ten times a second while the Timer shows ([tick]). */
    var now by mutableLongStateOf(clock.elapsed())
        private set

    /** Whether the phone shows alerts (Android: TimingAlerts.notifications), looked at when the Timer shows and on return. */
    var alerts by mutableStateOf(Alerts.ALLOWED)
        private set

    val alertsOn: Boolean get() = alerts == Alerts.ALLOWED

    /** A tap on a Timer alert asked for the Timer tab; the shell opens it. */
    var openRequested by mutableStateOf(false)

    /** The wall clock, for the alarm's day. */
    fun wall(): Long = clock.wall()

    /** The Timer redraws: the clocks read again (another part of the app may have changed them) at the time now. */
    fun tick() {
        now = clock.elapsed()
        state = store.load()
    }

    /** A change saved and the alerts scheduled for it (Android: TimingAlerts.update). */
    fun update(change: (TimingState) -> TimingState): TimingState {
        val next = change(store.load())
        store.save(next)
        schedule(next)
        state = next
        now = clock.elapsed()
        return next
    }

    /** The phone's alerts for [state]: the timer's end while it runs, the alarm while it is set (Android: TimingAlerts.schedule). */
    fun schedule(state: TimingState = store.load()) {
        device.timerAlert(if (state.countdown.running) state.countdown.remaining(clock.elapsed()) else null)
        device.alarmAlert(state.alarmAt.takeIf { it > 0 }?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone()) })
    }

    /**
     * A clock that ran out: the timer stops at zero and the alarm turns off, each ringing until stopped (Android:
     * TimingAlerts.checkDue). The phone's own alert was scheduled for that moment, so nothing is posted here.
     */
    fun checkDue(): TimingState {
        val before = store.load()
        val timerDue = before.countdown.running && before.countdown.remaining(clock.elapsed()) == 0L
        val alarmDue = before.alarmAt > 0 && before.alarmAt <= clock.wall()
        if (!timerDue && !alarmDue) return before.also { state = it }
        return update {
            before.copy(countdown = if (timerDue) before.countdown.finish() else before.countdown,
                alarmAt = if (alarmDue) 0 else before.alarmAt,
                timerRinging = before.timerRinging || timerDue, alarmRinging = before.alarmRinging || alarmDue)
        }
    }

    /** Stop, in the app or on the alert: what ran out stops ringing and its alerts go (Android: TimingAlerts.stop). */
    fun stop() {
        checkDue()
        update { it.copy(timerRinging = false, alarmRinging = false) }
        device.clearTimingAlerts()
    }

    /**
     * The clock or the time zone changed (Android: TimingReceiver's ACTION_TIME_CHANGED and ACTION_TIMEZONE_CHANGED): a
     * set alarm moves to the next [TimingState.alarmHour]:[TimingState.alarmMinute] on the wall clock now.
     */
    fun timeChanged() {
        if (store.load().alarmAt > 0) update { it.copy(alarmAt = nextAlarm(it.alarmHour, it.alarmMinute, clock.wall(), zone())) }
    }

    /**
     * The app is on screen again (Android: onResume's TimingAlerts.schedule): what ran out while it was away rings, a set
     * alarm keeps to the wall clock as it is now, the alerts are scheduled again and their permission looked at.
     */
    fun resumed() {
        checkDue()
        val alarm = store.load()
        if (alarm.alarmAt > 0 && nextAlarm(alarm.alarmHour, alarm.alarmMinute, clock.wall(), zone()) != alarm.alarmAt) timeChanged()
        else schedule()
        refreshAlerts()
    }

    /** Looks again whether alerts may show. */
    fun refreshAlerts() {
        device.alerts { alerts = it }
    }

    /**
     * "Enable alerts" (Android: HomeActivity.enableTimingNotifications): asks the first time; once turned off, only the
     * phone's settings can turn them on again, so those open.
     */
    fun enableAlerts() {
        device.alerts { status ->
            alerts = status
            when (status) {
                Alerts.NOT_ASKED -> device.askAlerts { refreshAlerts() }
                Alerts.DENIED -> device.openAlertSettings()
                Alerts.ALLOWED -> Unit
            }
        }
    }

    /** Timer, Stopwatch or Alarm, kept with the clocks. */
    fun selectTab(tab: Int) {
        update { it.copy(tab = tab.coerceIn(0, 2)) }
    }

    // ---- Timer

    /** Start, Pause or Resume. */
    fun timerPrimary() {
        val now = clock.elapsed()
        update { it.copy(countdown = if (it.countdown.running) it.countdown.pause(now) else it.countdown.start(now), timerRinging = false) }
    }

    /** Reset: back to the whole duration, and a ringing timer stops. */
    fun timerReset() {
        stop()
        update { it.copy(countdown = it.countdown.reset()) }
    }

    /** A preset, 30 sec, 1 min or 3 min. */
    fun preset(millis: Long) {
        update { it.copy(countdown = Countdown(millis)) }
    }

    /** The duration picked in the time's dialog; false for none ("Choose a duration"). */
    fun setDuration(millis: Long): Boolean {
        if (millis <= 0) return false
        update { it.copy(countdown = Countdown(millis), timerRinging = false) }
        return true
    }

    // ---- Stopwatch

    /** Start, Pause or Resume. */
    fun stopwatchPrimary() {
        val now = clock.elapsed()
        update { it.copy(stopwatch = it.stopwatch.toggle(now)) }
    }

    /** Lap while it runs, else Reset. */
    fun stopwatchSecondary() {
        val now = clock.elapsed()
        update { it.copy(stopwatch = if (it.stopwatch.running) it.stopwatch.lap(now) else Stopwatch()) }
    }

    // ---- Alarm

    /** Turn off, Enable alerts, or Set alarm, as the button says now. */
    fun alarmAction(): AlarmAction = when {
        store.load().alarmAt > 0 -> AlarmAction.TURN_OFF
        !alertsOn -> AlarmAction.ENABLE_ALERTS
        else -> AlarmAction.SET
    }

    fun alarmPrimary() {
        when (alarmAction()) {
            AlarmAction.TURN_OFF -> update { it.copy(alarmAt = 0) }
            AlarmAction.ENABLE_ALERTS -> enableAlerts()
            AlarmAction.SET -> update { it.copy(alarmAt = nextAlarm(it.alarmHour, it.alarmMinute, clock.wall(), zone()), alarmRinging = false) }
        }
    }

    /** The time picked in the alarm's dialog; a set alarm moves to it. */
    fun setAlarmTime(hour: Int, minute: Int) {
        update {
            it.copy(alarmHour = hour, alarmMinute = minute,
                alarmAt = if (it.alarmAt > 0) nextAlarm(hour, minute, clock.wall(), zone()) else 0)
        }
    }

    /** The phone's zone now, for the alarm's day. */
    fun zoneNow(): TimeZone = zone()
}
