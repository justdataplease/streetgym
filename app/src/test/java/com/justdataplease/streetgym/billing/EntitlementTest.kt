package com.justdataplease.streetgym.billing

import org.junit.Assert.*
import org.junit.Test

class EntitlementTest {
    private val plus = TierBilling.PLUS
    private val pro = TierBilling.PRO
    private fun payment(state: PaymentState, product: String = plus) = listOf(PaymentEvidence(product, state))

    @Test fun productIds() {
        assertEquals("plus", plus)
        assertEquals("pro", pro)
        assertEquals("yearly", TierBilling.PRO_BASE_PLAN)
        assertEquals("plus_owned", Entitlement.PLUS_OWNED)
        assertEquals("pro_active", Entitlement.PRO_ACTIVE)
        assertEquals("review_until", Entitlement.REVIEW_UNTIL)
    }
    @Test fun purchasedGrantsImmediately() {
        assertEquals(Ownership(true, false), reconcileEntitlement(plus, false, payment(PaymentState.Purchased), false))
    }
    @Test fun pendingNeverGrantsAccess() {
        assertEquals(Ownership(false, true), reconcileEntitlement(plus, false, payment(PaymentState.Pending), true))
        assertEquals(Ownership(false, true), reconcileEntitlement(pro, false, payment(PaymentState.Pending, pro), false))
    }
    @Test fun unrelatedProductCannotUnlock() {
        assertEquals(Ownership(false, false), reconcileEntitlement(plus, false,
            listOf(PaymentEvidence("full_program", PaymentState.Purchased)), true))
    }
    @Test fun partialEmptyUpdateRetainsCachedPurchase() {
        assertTrue(reconcileEntitlement(plus, true, emptyList(), false).owned)
        assertTrue(reconcileEntitlement(pro, true, emptyList(), false).owned)
    }
    @Test fun offlineCacheSurvivesPendingOrUnknownEvidence() {
        assertTrue(reconcileEntitlement(plus, true, payment(PaymentState.Pending), false).owned)
        assertTrue(reconcileEntitlement(plus, true, payment(PaymentState.Other), false).owned)
    }
    @Test fun successfulCompleteEmptyQueryRevokesRefundOrLapsedSubscription() {
        assertFalse(reconcileEntitlement(plus, true, emptyList(), true).owned)
        assertFalse(reconcileEntitlement(pro, true, emptyList(), true).owned)
    }
    @Test fun eachProductIsReconciledOnItsOwn() {
        val evidence = payment(PaymentState.Purchased, pro)
        assertTrue(reconcileEntitlement(pro, false, evidence, true).owned)
        assertFalse(reconcileEntitlement(plus, false, evidence, true).owned)
    }
    @Test fun unspecifiedPaymentStateDoesNotUnlock() {
        assertFalse(reconcileEntitlement(plus, false, payment(PaymentState.Other), false).owned)
    }

    @Test fun ownershipPicksTheHighestTier() {
        assertEquals(Tier.FREE, tierFor(plusOwned = false, proActive = false, debug = false, simulated = null))
        assertEquals(Tier.PLUS, tierFor(plusOwned = true, proActive = false, debug = false, simulated = null))
        assertEquals(Tier.PRO, tierFor(plusOwned = false, proActive = true, debug = false, simulated = null))
        assertEquals(Tier.PRO, tierFor(plusOwned = true, proActive = true, debug = false, simulated = null))
    }
    @Test fun aLapsedProSubscriptionFallsBackToPlusWhenOwned() {
        assertEquals(Tier.PLUS, tierFor(plusOwned = true, proActive = false, debug = false, simulated = null))
    }
    @Test fun debugBuildsGetProUnlessATierIsSimulated() {
        assertEquals(Tier.PRO, tierFor(plusOwned = false, proActive = false, debug = true, simulated = null))
        Tier.entries.forEach { assertEquals(it, tierFor(plusOwned = false, proActive = false, debug = true, simulated = it)) }
    }
    @Test fun aSimulatedTierStillCountsCachedOwnership() {
        assertEquals(Tier.PLUS, tierFor(plusOwned = true, proActive = false, debug = true, simulated = Tier.FREE))
        assertEquals(Tier.PRO, tierFor(plusOwned = false, proActive = true, debug = true, simulated = Tier.PLUS))
    }
    @Test fun releaseBuildsIgnoreTheSimulation() {
        assertEquals(Tier.FREE, tierFor(plusOwned = false, proActive = false, debug = false, simulated = Tier.PRO))
        assertEquals(Tier.PLUS, tierFor(plusOwned = true, proActive = false, debug = false, simulated = Tier.FREE))
    }
    @Test fun tiersUnlockInOrder() {
        assertEquals(listOf(3, 30, 300), Tier.entries.map { it.savedDays })
        assertFalse(Tier.FREE.adFree || Tier.FREE.parks || Tier.FREE.renameLevels)
        assertTrue(Tier.PLUS.adFree && Tier.PLUS.parks && Tier.PLUS.renameLevels)
        assertFalse(Tier.FREE.account || Tier.PLUS.account)
        assertTrue(Tier.PRO.adFree && Tier.PRO.account)
    }
    @Test fun savedDaysShareOneLimitAndNothingIsRemovedAboveIt() {
        assertTrue(SavedLimit.canAdd(Tier.FREE, 2))
        assertFalse(SavedLimit.canAdd(Tier.FREE, 3))
        assertTrue(SavedLimit.canAdd(Tier.PLUS, 29))
        assertFalse(SavedLimit.canAdd(Tier.PRO, 300))
        assertTrue(SavedLimit.overLimit(Tier.FREE, 12))
        assertFalse(SavedLimit.overLimit(Tier.PLUS, 30))
    }
    @Test fun onlyPlusAndProMakeLists() {
        assertEquals(listOf(0, 3, 10), Tier.entries.map { it.workoutLists })
        assertEquals(listOf(0, 10, 30), Tier.entries.map { it.daysPerList })
        listOf(Tier.PLUS, Tier.PRO).forEach { assertEquals(it.savedDays, it.workoutLists * it.daysPerList) }
        assertFalse(Tier.FREE.lists)
        assertTrue(Tier.PLUS.lists && Tier.PRO.lists)
        assertFalse(SavedLimit.canAddList(Tier.FREE, 0))
        assertTrue(SavedLimit.canAddList(Tier.PLUS, 2))
        assertFalse(SavedLimit.canAddList(Tier.PLUS, 3))
        assertTrue(SavedLimit.canAddList(Tier.PRO, 9))
        assertFalse(SavedLimit.canAddList(Tier.PRO, 10))
        assertTrue(SavedLimit.canAddToList(Tier.PLUS, 9))
        assertFalse(SavedLimit.canAddToList(Tier.PLUS, 10))
        assertTrue(SavedLimit.canAddToList(Tier.PRO, 29))
        assertFalse(SavedLimit.canAddToList(Tier.PRO, 30))
    }
}
