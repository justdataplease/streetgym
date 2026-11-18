package com.justdataplease.streetgym.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The products, by Android's Play ids (android-streetgym, billing/TierBilling.kt PLUS, PRO and PRO_LIFE), so App Store
 * Connect uses the same ones: Plus and Pro for life are one payment each (non-consumable); [PRO] is Pro by the year,
 * Android's subscription `pro` with its base plan `yearly`, an auto-renewable yearly subscription on the App Store.
 */
object Products {
    const val PLUS = "plus"
    const val PRO = "pro"
    const val PRO_LIFE = "pro_life"
    val ALL = listOf(PLUS, PRO_LIFE, PRO)
}

/** Whether the store answered (Android: TierBilling.Status): prices on the way, prices in, or no store to reach. */
enum class StoreStatus { LOADING, READY, UNAVAILABLE }

/**
 * What the store says the athlete has: [owned] products paid for and neither refunded nor lapsed, and [pending] ones
 * waiting for a payment to be confirmed (Ask to Buy, a bank's check). [complete]: the answer covers every product, so
 * one missing from [owned] isn't owned (Android: a successful query of the product's type); a partial answer only adds.
 */
data class StoreEntitlements(
    val owned: Set<String> = emptySet(),
    val pending: Set<String> = emptySet(),
    val complete: Boolean = true,
)

/** How a purchase ended: paid, waiting for the payment, cancelled by the athlete, or the store couldn't do it. */
enum class Bought { BOUGHT, PENDING, CANCELLED, FAILED }

/** What Restore purchases found (Android: TierBilling.Restore). */
enum class Restored { PRO, PLUS, NOT_OWNED, UNAVAILABLE }

/**
 * Where Plus and Pro are bought: StoreKit 2 on the iPhone (Swift, iosApp/StreetGym/StoreKitStore.swift, through
 * iosMain's IosStore), a fake on the desktop and in tests, [None] where nothing can be bought. Only the store's own
 * formatted prices are ever shown (Android: "only Play's formatted prices").
 */
interface Store {
    /** Loading until the products answer; Unavailable when none came. */
    val status: StateFlow<StoreStatus>

    /** Each product's price as the store writes it for the athlete's country ("€14.99"), by product id. */
    val prices: StateFlow<Map<String, String>>

    /** What the athlete has, each time it changes: at start, after a purchase or a restore, a refund, a lapse; null until the store first answers. */
    val entitlements: StateFlow<StoreEntitlements?>

    /** Reads the products and what the athlete has again (Android: TierBilling.connect, on every return to the app). */
    fun connect()

    /** Buys [product]; [done] hears how it ended, after [entitlements] has the change. */
    fun buy(product: String, done: (Bought) -> Unit)

    /** Asks the store for every purchase again (Apple: AppStore.sync); [done] true once it answered, false when it couldn't be reached. */
    fun restore(done: (Boolean) -> Unit)

    /** The store's own sheet to redeem an offer code (Android: Play's redeem page); [done] false when it can't open. */
    fun redeemCode(done: (Boolean) -> Unit = {})

    /** The yearly subscription's own page in the store, to cancel or change it (Android: Play's subscriptions page). */
    fun manageSubscription() {}

    /** No store: nothing can be bought or restored (previews, and tests that don't buy). */
    object None : Store {
        override val status: StateFlow<StoreStatus> = MutableStateFlow(StoreStatus.UNAVAILABLE)
        override val prices: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
        override val entitlements: StateFlow<StoreEntitlements?> = MutableStateFlow(null)
        override fun connect() {}
        override fun buy(product: String, done: (Bought) -> Unit) = done(Bought.FAILED)
        override fun restore(done: (Boolean) -> Unit) = done(false)
        override fun redeemCode(done: (Boolean) -> Unit) = done(false)
    }
}

/** A payment as the store reports it (Android: billing/Entitlement.kt PaymentState). */
enum class PaymentState { Purchased, Pending, Other }

/** One product's payment (Android: PaymentEvidence). */
data class PaymentEvidence(val product: String, val state: PaymentState)

/** Whether a product is owned, and whether a payment for it is waiting (Android: Ownership). */
data class Ownership(val owned: Boolean, val pending: Boolean)

/**
 * Only confirmed purchases grant access; only a complete answer can take it away (Android: reconcileEntitlement, line
 * for line). [previouslyOwned] is the cached ownership; [complete]: the answer covers [product].
 */
fun reconcileEntitlement(product: String, previouslyOwned: Boolean, purchases: List<PaymentEvidence>, complete: Boolean): Ownership {
    val ours = purchases.filter { it.product == product }
    val paid = ours.any { it.state == PaymentState.Purchased }
    val owned = if (complete || paid) paid else previouslyOwned
    return Ownership(owned, !owned && ours.any { it.state == PaymentState.Pending })
}
