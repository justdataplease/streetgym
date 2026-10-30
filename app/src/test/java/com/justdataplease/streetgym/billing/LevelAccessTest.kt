package com.justdataplease.streetgym.billing

import org.junit.Assert.*
import org.junit.Test

class LevelAccessTest {
    @Test fun levelsOneToThreeAreFree() {
        (1..3).forEach { level -> Tier.entries.forEach { assertFalse(LevelAccess.locked(level, it)) } }
    }
    @Test fun levelsFourToSixNeedPlus() {
        (4..6).forEach {
            assertEquals(Tier.PLUS, LevelAccess.tierFor(it))
            assertTrue(LevelAccess.locked(it, Tier.FREE))
            assertFalse(LevelAccess.locked(it, Tier.PLUS))
            assertFalse(LevelAccess.locked(it, Tier.PRO))
        }
    }
    @Test fun levelsSevenToTenNeedPro() {
        (7..10).forEach {
            assertEquals(Tier.PRO, LevelAccess.tierFor(it))
            assertTrue(LevelAccess.locked(it, Tier.FREE))
            assertTrue(LevelAccess.locked(it, Tier.PLUS))
            assertFalse(LevelAccess.locked(it, Tier.PRO))
        }
    }
    @Test fun anyLaterLevelStaysLocked() {
        assertTrue(LevelAccess.locked(11, Tier.PLUS))
    }
}
