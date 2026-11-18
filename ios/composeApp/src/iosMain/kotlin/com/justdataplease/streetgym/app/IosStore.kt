package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.Bought
import com.justdataplease.streetgym.app.data.Store
import com.justdataplease.streetgym.app.data.StoreEntitlements
import com.justdataplease.streetgym.app.data.StoreStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What Swift's StoreKit 2 store does (iosApp/StreetGym/StoreKitStore.swift), as Kotlin can't read StoreKit 2's Swift
 * API. Swift sees it as the protocol `NativeStore`; everything it reports comes back on the main thread through
 * [NativeStoreListener].
 */
interface NativeStore {
    /** Starts listening to the App Store's transactions, then loads the products and reads what the athlete has. */
    fun start(listener: NativeStoreListener)

    /** Loads the products again if they didn't come, and reads what the athlete has again. */
    fun refresh()

    /** Buys the product with [productId]. */
    fun buy(productId: String)

    /** Asks the App Store for every purchase again (AppStore.sync, which may ask the athlete to sign in). */
    fun restore()

    /** Apple's sheet to redeem an offer code. */
    fun redeemCode()

    /** Apple's sheet for the athlete's subscriptions (cancel or change the yearly Pro). */
    fun manageSubscriptions()
}

/** What Swift's store tells the app; Swift sees it as the protocol `NativeStoreListener`. */
interface NativeStoreListener {
    /** The products' prices as the App Store writes them, by product id; empty when none could be loaded. */
    fun productsLoaded(prices: Map<String, String>)

    /** Every product the athlete owns now (verified, neither refunded nor expired): a complete answer. */
    fun entitlementsRead(owned: List<String>)

    fun purchased(productId: String)

    /** Waiting for the payment: Ask to Buy, or a bank's check. */
    fun pending(productId: String)

    fun cancelled(productId: String)

    fun failed(productId: String)

    /** Restore purchases ended: [reached] false when the App Store couldn't be reached. */
    fun restoreEnded(reached: Boolean)

    /** The offer-code sheet [opened], or couldn't. */
    fun redeemEnded(opened: Boolean)
}

/**
 * The app's [Store] over Swift's StoreKit 2 ([NativeStore]): Swift does the talking to the App Store, Kotlin keeps what it
 * said as flows. A purchase's end is heard after what the athlete owns has been read again.
 */
internal class IosStore(private val native: NativeStore) : Store, NativeStoreListener {
    override val status = MutableStateFlow(StoreStatus.LOADING)
    override val prices = MutableStateFlow<Map<String, String>>(emptyMap())
    override val entitlements = MutableStateFlow<StoreEntitlements?>(null)
    private var owned: Set<String> = emptySet()
    private var waiting: Set<String> = emptySet()
    private val buying = mutableMapOf<String, (Bought) -> Unit>()
    private val restoring = mutableListOf<(Boolean) -> Unit>()
    private val redeeming = mutableListOf<(Boolean) -> Unit>()
    private var started = false

    override fun connect() {
        if (started) native.refresh() else {
            started = true
            native.start(this)
        }
    }

    override fun buy(product: String, done: (Bought) -> Unit) {
        buying[product]?.invoke(Bought.CANCELLED)
        buying[product] = done
        native.buy(product)
    }

    override fun restore(done: (Boolean) -> Unit) {
        restoring += done
        native.restore()
    }

    override fun redeemCode(done: (Boolean) -> Unit) {
        redeeming += done
        native.redeemCode()
    }

    override fun manageSubscription() = native.manageSubscriptions()

    override fun productsLoaded(prices: Map<String, String>) {
        this.prices.value = prices
        status.value = if (prices.isEmpty()) StoreStatus.UNAVAILABLE else StoreStatus.READY
    }

    override fun entitlementsRead(owned: List<String>) {
        this.owned = owned.toSet()
        waiting = waiting - this.owned
        publish()
    }

    override fun purchased(productId: String) = ended(productId, Bought.BOUGHT)

    override fun pending(productId: String) {
        waiting = waiting + productId
        publish()
        ended(productId, Bought.PENDING)
    }

    override fun cancelled(productId: String) = ended(productId, Bought.CANCELLED)

    override fun failed(productId: String) = ended(productId, Bought.FAILED)

    override fun restoreEnded(reached: Boolean) {
        val waitingFor = restoring.toList()
        restoring.clear()
        waitingFor.forEach { it(reached) }
    }

    override fun redeemEnded(opened: Boolean) {
        val waitingFor = redeeming.toList()
        redeeming.clear()
        waitingFor.forEach { it(opened) }
    }

    private fun ended(productId: String, result: Bought) {
        buying.remove(productId)?.invoke(result)
    }

    private fun publish() {
        entitlements.value = StoreEntitlements(owned, waiting, complete = true)
    }
}
