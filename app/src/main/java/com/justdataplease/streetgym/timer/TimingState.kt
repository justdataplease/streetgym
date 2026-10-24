package com.justdataplease.streetgym.timer

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Adapted from Just a Toolbox: elapsed time is independent of wall-clock changes. */
data class Countdown(val total: Long = 60_000L, val running: Boolean = false,
    val endsAt: Long = 0L, val left: Long = total) {
    fun remaining(now: Long) = if (running) (endsAt - now).coerceAtLeast(0L) else left
    fun start(now: Long): Countdown = if (running || total <= 0) this else {
        val duration = left.takeIf { it > 0 } ?: total
        copy(running = true, endsAt = now + duration, left = duration)
    }
    fun pause(now: Long) = if (running) copy(running = false, left = remaining(now)) else this
    fun reset() = Countdown(total)
    fun finish() = copy(running = false, left = 0L)
}

/** Toolbox stopwatch behaviour, using elapsed-realtime milliseconds and bounded lap storage. */
data class Stopwatch(val running: Boolean = false, val startedAt: Long = 0L,
    val banked: Long = 0L, val laps: List<Long> = emptyList()) {
    fun elapsed(now: Long) = banked + if (running) (now - startedAt).coerceAtLeast(0L) else 0L
    fun toggle(now: Long) = if (running) copy(running = false, banked = elapsed(now))
        else copy(running = true, startedAt = now)
    fun lap(now: Long) = if (running) copy(laps = (laps + elapsed(now)).takeLast(100)) else this
}

data class TimingState(val countdown: Countdown = Countdown(), val stopwatch: Stopwatch = Stopwatch(),
    val alarmAt: Long = 0L, val alarmHour: Int = 7, val alarmMinute: Int = 0,
    val timerRinging: Boolean = false, val alarmRinging: Boolean = false, val tab: Int = 0)

fun nextAlarm(hour: Int, minute: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    require(hour in 0..23 && minute in 0..59)
    return Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
}

fun clockText(milliseconds: Long, roundUp: Boolean = false): String {
    val seconds = (milliseconds.coerceAtLeast(0) + if (roundUp) 999 else 0) / 1000
    return if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
