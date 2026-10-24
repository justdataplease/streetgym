package com.justdataplease.streetgym.timer

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject

/** Separate from workout progress and rest timers. Carries running clocks across a reboot. */
class TimingStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("streetgym_timing", Context.MODE_PRIVATE)
    private fun boot() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun load(): TimingState = synchronized(lock) {
        val raw = prefs.getString("state", null) ?: return@synchronized TimingState()
        runCatching {
            val o = JSONObject(raw)
            val elapsed = SystemClock.elapsedRealtime()
            val wall = System.currentTimeMillis()
            val rebooted = o.optInt("boot", -1) != boot()
            var timer = Countdown(o.getLong("total"), o.getBoolean("running"), o.getLong("end"), o.getLong("left"))
            var watch = Stopwatch(o.getBoolean("watchRunning"), o.getLong("watchStart"), o.getLong("banked"),
                o.optString("laps").split(',').mapNotNull { it.toLongOrNull() }.filter { it >= 0 }.takeLast(100))
            if (rebooted) {
                if (timer.running) timer = timer.copy(endsAt = elapsed + (o.getLong("wallEnd") - wall).coerceAtLeast(0))
                if (watch.running) watch = watch.copy(startedAt = elapsed,
                    banked = watch.banked + (wall - o.getLong("watchWallStart")).coerceAtLeast(0))
            }
            TimingState(timer, watch, o.optLong("alarmAt"), o.optInt("alarmHour", 7), o.optInt("alarmMinute"),
                o.optBoolean("timerRinging"), o.optBoolean("alarmRinging"), o.optInt("tab").coerceIn(0, 2))
                .also { if (rebooted) save(it) }
        }.getOrDefault(TimingState())
    }

    fun save(state: TimingState) = synchronized(lock) {
        val elapsed = SystemClock.elapsedRealtime(); val wall = System.currentTimeMillis()
        val c = state.countdown; val w = state.stopwatch
        val o = JSONObject().put("boot", boot()).put("total", c.total).put("running", c.running)
            .put("end", c.endsAt).put("left", c.left).put("wallEnd", wall + c.endsAt - elapsed)
            .put("watchRunning", w.running).put("watchStart", w.startedAt).put("banked", w.banked)
            .put("watchWallStart", wall - (elapsed - w.startedAt)).put("laps", w.laps.joinToString(","))
            .put("alarmAt", state.alarmAt).put("alarmHour", state.alarmHour).put("alarmMinute", state.alarmMinute)
            .put("timerRinging", state.timerRinging).put("alarmRinging", state.alarmRinging).put("tab", state.tab)
        check(prefs.edit().putString("state", o.toString()).commit())
    }

    companion object { private val lock = Any() }
}
