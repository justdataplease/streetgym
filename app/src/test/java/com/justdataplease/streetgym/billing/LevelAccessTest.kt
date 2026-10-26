package com.justdataplease.streetgym.billing

import org.junit.Assert.*
import org.junit.Test

class LevelAccessTest {
    @Test fun levelsOneToThreeAreFree() {
        (1..3).forEach { assertFalse(LevelAccess.locked(it, owned = false)) }
    }
    @Test fun levelsFourToSevenNeedPlus() {
        (4..7).forEach {
            assertTrue(LevelAccess.locked(it, owned = false))
            assertFalse(LevelAccess.locked(it, owned = true))
        }
    }
    @Test fun ownershipNeverLocksAFreeLevel() {
        (1..3).forEach { assertFalse(LevelAccess.locked(it, owned = true)) }
    }
    @Test fun anyLaterLevelStaysLocked() {
        assertTrue(LevelAccess.locked(8, owned = false))
    }
}
