package com.justdataplease.streetgym.billing

import org.junit.Assert.*
import org.junit.Test

/** The Play review code, with a made-up test code and its hash: the real code is never in the repository. */
class ReviewAccessTest {
    private val code = "TEST-REVIEW-0001"
    private val hash = "2b9940e79fb2dc90c44f54245fc5b29e1d50ecd23d3e2ce9d10c53658f97f97a"
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test fun sha256IsLowercaseHex() {
        assertEquals(hash, ReviewAccess.sha256Hex(code))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ReviewAccess.sha256Hex(""))
    }
    @Test fun theRealHashIsWellFormedAndNotTheTestOne() {
        assertTrue(ReviewAccess.CODE_SHA256.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(hash, ReviewAccess.CODE_SHA256)
        assertFalse(ReviewAccess.matches(code))
    }
    @Test fun theRightCodeUnlocksThirtyDays() {
        assertEquals(now + 30 * day, ReviewAccess.unlockUntil(code, now, hash))
        assertEquals(30, ReviewAccess.DAYS)
    }
    @Test fun spacesAndLowercaseAreForgivenButHyphensAreNot() {
        assertTrue(ReviewAccess.matches("  test-review-0001 \n", hash))
        assertTrue(ReviewAccess.matches(code, hash.uppercase()))
        assertFalse(ReviewAccess.matches("TESTREVIEW0001", hash))
        assertFalse(ReviewAccess.matches("TEST REVIEW 0001", hash))
    }
    @Test fun aWrongCodeUnlocksNothing() {
        assertNull(ReviewAccess.unlockUntil("TEST-REVIEW-0002", now, hash))
        assertNull(ReviewAccess.unlockUntil("", now, hash))
        assertNull(ReviewAccess.unlockUntil("   ", now, hash))
        assertNull(ReviewAccess.unlockUntil(hash, now, hash))
    }
    @Test fun accessRunsUntilItsEnd() {
        val until = ReviewAccess.unlockUntil(code, now, hash)!!
        assertTrue(ReviewAccess.active(until, now))
        assertTrue(ReviewAccess.active(until, until - 1))
        assertFalse(ReviewAccess.active(until, until))
        assertFalse(ReviewAccess.active(until, until + day))
        assertFalse(ReviewAccess.active(0L, now))
    }
    @Test fun aValidCodeMakesEveryTierProInRelease() {
        val until = ReviewAccess.unlockUntil(code, now, hash)!!
        val review = ReviewAccess.active(until, now + 29 * day)
        assertEquals(Tier.PRO, tierFor(plusOwned = false, proActive = false, debug = false, simulated = null, review = review))
        assertEquals(Tier.PRO, tierFor(plusOwned = true, proActive = false, debug = false, simulated = null, review = review))
        assertEquals(Tier.PRO, tierFor(plusOwned = false, proActive = false, debug = true, simulated = Tier.FREE, review = review))
    }
    @Test fun anExpiredCodeFallsBackToOwnership() {
        val until = ReviewAccess.unlockUntil(code, now, hash)!!
        val review = ReviewAccess.active(until, now + 31 * day)
        assertFalse(review)
        assertEquals(Tier.FREE, tierFor(plusOwned = false, proActive = false, debug = false, simulated = null, review = review))
        assertEquals(Tier.PLUS, tierFor(plusOwned = true, proActive = false, debug = false, simulated = null, review = review))
    }
    @Test fun aWrongCodeLeavesTheTierAsItWas() {
        val until = ReviewAccess.unlockUntil("TEST-REVIEW-9999", now, hash) ?: 0L
        val review = ReviewAccess.active(until, now)
        assertEquals(Tier.FREE, tierFor(plusOwned = false, proActive = false, debug = false, simulated = null, review = review))
        assertEquals(Tier.PLUS, tierFor(plusOwned = true, proActive = false, debug = false, simulated = null, review = review))
    }
}
