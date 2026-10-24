package com.justdataplease.streetgym.ads

/** Only foreground use counts. A missed break never creates an ad backlog. */
class AdBreakClock(private var banked: Long = 0, val interval: Long = 10 * 60_000L) {
    private var started: Long? = null
    fun resume(now: Long) { if (started == null) started = now }
    fun elapsed(now: Long) = banked + (started?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
    fun pause(now: Long): Long { banked = elapsed(now); started = null; return banked }
    fun due(now: Long) = elapsed(now) >= interval
    fun shown(now: Long) { banked = 0; if (started != null) started = now }
}
