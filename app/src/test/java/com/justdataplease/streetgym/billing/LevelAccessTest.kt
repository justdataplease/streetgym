package com.justdataplease.streetgym.billing

import org.junit.Assert.*
import org.junit.Test

class LevelAccessTest {
    @Test fun levelsOneToThreeAreFree() {
        (1..3).forEach { level -> Tier.entries.forEach { assertFalse(LevelAccess.locked(level, it)) } }
    }
    @Test fun levelsFourAndFiveNeedPlus() {
        (4..5).forEach {
            assertEquals(Tier.PLUS, LevelAccess.tierFor(it))
            assertTrue(LevelAccess.locked(it, Tier.FREE))
            assertFalse(LevelAccess.locked(it, Tier.PLUS))
            assertFalse(LevelAccess.locked(it, Tier.PRO))
        }
    }
    @Test fun levelsSixAndSevenNeedPro() {
        (6..7).forEach {
            assertEquals(Tier.PRO, LevelAccess.tierFor(it))
            assertTrue(LevelAccess.locked(it, Tier.FREE))
            assertTrue(LevelAccess.locked(it, Tier.PLUS))
            assertFalse(LevelAccess.locked(it, Tier.PRO))
        }
    }
    @Test fun anyLaterLevelStaysLocked() {
        assertTrue(LevelAccess.locked(8, Tier.PLUS))
    }
}
