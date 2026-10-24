package com.justdataplease.streetgym.billing

import org.junit.Assert.*
import org.junit.Test

class AdEntitlementTest {
    private fun payment(state: PaymentState) = listOf(PaymentEvidence("remove_ads", state))
    @Test fun purchasedGrantsImmediately() {
        assertEquals(AdEntitlement(true, false), reconcileEntitlement(false, payment(PaymentState.Purchased), false))
    }
    @Test fun pendingNeverGrantsAccess() {
        assertEquals(AdEntitlement(false, true), reconcileEntitlement(false, payment(PaymentState.Pending), true))
    }
    @Test fun unrelatedProductCannotRemoveAds() {
        assertEquals(AdEntitlement(false, false), reconcileEntitlement(false,
            listOf(PaymentEvidence("supporter", PaymentState.Purchased)), true))
    }
    @Test fun partialEmptyUpdateRetainsCachedPurchase() {
        assertTrue(reconcileEntitlement(true, emptyList(), false).owned)
    }
    @Test fun successfulCompleteEmptyQueryRevokesRefundedEntitlement() {
        assertFalse(reconcileEntitlement(true, emptyList(), true).owned)
    }
    @Test fun restoreFindsConfirmedPurchaseAmongOtherProducts() {
        assertEquals(AdEntitlement(true, false), reconcileEntitlement(false,
            payment(PaymentState.Purchased) + PaymentEvidence("another_product", PaymentState.Pending), true))
    }
    @Test fun unspecifiedPaymentStateDoesNotUnlock() {
        assertFalse(reconcileEntitlement(false, payment(PaymentState.Other), false).owned)
    }
}
