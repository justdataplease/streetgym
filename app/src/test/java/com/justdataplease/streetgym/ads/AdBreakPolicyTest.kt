package com.justdataplease.streetgym.ads

import com.justdataplease.streetgym.billing.Tier
import org.junit.Assert.*
import org.junit.Test

class AdBreakPolicyTest {
    private fun show(tier: Tier = Tier.FREE, due: Boolean = true, resumed: Boolean = true, timing: Boolean = false,
                     covered: Boolean = false, consent: Boolean = true) =
        AdBreakPolicy.mayShow(tier.adFree, due, resumed, timing, covered, consent)

    @Test fun theGapIsFiveMinutesOfUse() {
        assertEquals(5 * 60_000L, AdBreakClock.GAP_MS)
        val clock = AdBreakClock(); clock.resume(0)
        assertFalse(clock.due(299_999)); assertTrue(clock.due(300_000))
        clock.shown(300_000)
        assertFalse(clock.due(599_999)); assertTrue(clock.due(600_000))
    }
    @Test fun aFreshInstallWaitsTheWholeGap() {
        // App open is never a break, and the clock starts empty: nothing in the first five minutes.
        val clock = AdBreakClock(); clock.resume(1_000)
        assertFalse(show(due = clock.due(1_000)))
        assertFalse(show(due = clock.due(1_000 + AdBreakClock.GAP_MS - 1)))
    }
    @Test fun onlyFreeSeesIt() {
        assertTrue(show(Tier.FREE))
        assertFalse(show(Tier.PLUS))
        assertFalse(show(Tier.PRO))
        assertFalse(AdBreakPolicy.mayLoad(Tier.PLUS.adFree, consent = true, elapsed = AdBreakClock.GAP_MS))
        assertFalse(AdBreakPolicy.mayLoad(Tier.PRO.adFree, consent = true, elapsed = AdBreakClock.GAP_MS))
    }
    @Test fun neverBeforeTheGapOrDuringTimingOrOverAPaywall() {
        assertFalse(show(due = false))
        assertFalse(show(timing = true))
        assertFalse(show(covered = true))
        assertFalse(show(consent = false))
        assertFalse(show(resumed = false))
    }
    @Test fun loadsAMinuteAheadWithConsent() {
        assertFalse(AdBreakPolicy.mayLoad(false, consent = true, elapsed = AdBreakClock.GAP_MS - 60_001))
        assertTrue(AdBreakPolicy.mayLoad(false, consent = true, elapsed = AdBreakClock.GAP_MS - 60_000))
        assertFalse(AdBreakPolicy.mayLoad(false, consent = false, elapsed = AdBreakClock.GAP_MS))
    }
    @Test fun theListAdSitsAfterTheThirdRow() {
        // 100 classes plus the ad: rows 0-2, the ad at 3, then row 3 at position 4 and the last at 100.
        assertEquals(listOf(0, 1, 2, null, 3, 4), (0..5).map { adRowBaseIndex(it, 100, 3) })
        assertEquals(99, adRowBaseIndex(100, 100, 3))
        // A shorter list ends with the ad.
        assertEquals(listOf(0, 1, null), (0..2).map { adRowBaseIndex(it, 2, 3) })
    }
}
