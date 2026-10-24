package com.justdataplease.monetization

/** Controls retries and rejects late SDK callbacks after a screen or consent change. */
internal class AdRequestGate(private val retryGapMs: Long = 60_000L) {
    private var generation = 0L
    private var pending: Long? = null
    private var lastAttempt: Long? = null

    fun begin(now: Long, consent: Boolean, visible: Boolean): Long? {
        if (!consent || !visible || pending != null || retryAfter(now) > 0) return null
        lastAttempt = now
        return (++generation).also { pending = it }
    }

    fun retryAfter(now: Long): Long = lastAttempt?.let { (retryGapMs - (now - it)).coerceAtLeast(0) } ?: 0

    fun finish(token: Long): Boolean {
        if (pending != token) return false
        pending = null
        return true
    }

    fun invalidate() { generation++; pending = null }
}
