package com.justdataplease.streetgym.ads

/** Only foreground use counts. A missed break never creates an ad backlog. */
class AdBreakClock(private var banked: Long = 0, val interval: Long = GAP_MS) {
    private var started: Long? = null
    fun resume(now: Long) { if (started == null) started = now }
    fun elapsed(now: Long) = banked + (started?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
    fun pause(now: Long): Long { banked = elapsed(now); started = null; return banked }
    fun due(now: Long) = elapsed(now) >= interval
    fun shown(now: Long) { banked = 0; if (started != null) started = now }

    companion object {
        /** At most one full-screen ad every five minutes of use (the owner, 2026-10-30; was ten). */
        const val GAP_MS = 5 * 60_000L
    }
}

/**
 * When the full-screen ad may show: Free only, at a natural break (a finished section, or leaving a workout), once the
 * gap has passed, never while a rest, timer, stopwatch or alarm runs, never over the paywall or the consent form, and
 * only with ad consent. App open is never a break, so the ad never greets an opening app.
 */
object AdBreakPolicy {
    fun mayShow(adFree: Boolean, due: Boolean, resumed: Boolean, timing: Boolean, covered: Boolean, consent: Boolean): Boolean =
        !adFree && due && resumed && !timing && !covered && consent

    /** Loads a minute before the gap ends, so an ad is ready at the next break. */
    fun mayLoad(adFree: Boolean, consent: Boolean, elapsed: Long, interval: Long = AdBreakClock.GAP_MS): Boolean =
        !adFree && consent && elapsed >= interval - 60_000L
}
