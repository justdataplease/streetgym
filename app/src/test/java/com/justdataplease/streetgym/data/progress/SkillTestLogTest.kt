package com.justdataplease.streetgym.data.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillTestLogTest {
    private val day = SkillTestLog.ATTEMPT_MILLIS

    @Test fun aRungIsTestedAtMostOnceADay() {
        val log = SkillTestLog().record("tuck_planche", passed = false, at = 1_000)
        assertEquals(1_000 + day, log.nextAttemptAt("tuck_planche", 2_000))
        assertNull(log.nextAttemptAt("tuck_planche", 1_000 + day))
        assertNull(log.nextAttemptAt("full_front", 2_000))
        // A clock moved back doesn't lock the test.
        assertNull(log.nextAttemptAt("tuck_planche", 500))
    }

    @Test fun aPassIsKeptAtItsFirstTime() {
        val log = SkillTestLog().record("lsit", true, 10).record("lsit", false, 20).record("lsit", true, 30)
        assertEquals(mapOf("lsit" to 10L), log.passed)
        assertEquals(mapOf("lsit" to 30L), log.attempts)
    }

    @Test fun devicesMergeWithoutLosingAPass() {
        val phone = SkillTestLog().record("lsit", true, 50).record("pistol_squat", false, 70)
        val tablet = SkillTestLog().record("lsit", true, 40).record("dragon_flag", true, 60).record("pistol_squat", false, 20)
        val merged = phone.merge(tablet)
        assertEquals(mapOf("lsit" to 40L, "dragon_flag" to 60L), merged.passed)
        assertEquals(mapOf("lsit" to 50L, "pistol_squat" to 70L, "dragon_flag" to 60L), merged.attempts)
        assertEquals(merged, tablet.merge(phone))
    }

    @Test fun theLogRoundTripsAndReadsNothingFromGarbage() {
        val log = SkillTestLog().record("lsit", true, 10).record("v_sit", false, 20)
        assertEquals(log, SkillTestLog.decode(log.encode()))
        assertEquals(SkillTestLog(), SkillTestLog.decode(null))
        assertEquals(SkillTestLog(), SkillTestLog.decode("not json"))
    }
}
