package com.justdataplease.streetgym.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*

/** One non-consumable purchase, adapted from the Toolbox Supporter integration. */
class RemoveAdsBilling(context: Context) : PurchasesUpdatedListener {
    enum class Status { Loading, Ready, Pending, Unavailable }
    enum class Restore { Restored, NotOwned, Unavailable }
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var owned = prefs.getBoolean("owned", false); private set
    var price: String? = null; private set
    var status = Status.Loading; private set
    val listeners = mutableSetOf<() -> Unit>()
    private var product: ProductDetails? = null
    private var connecting = false
    private var closed = false
    private val acknowledging = mutableSetOf<String>()
    private val callbacks = mutableListOf<(Restore) -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private val client = BillingClient.newBuilder(context.applicationContext).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()

    fun connect() {
        if (closed || connecting) return
        if (client.isReady) { queryProduct(); refresh(); return }
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (closed) return
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) { queryProduct(); refresh() }
                else { status = Status.Unavailable; changed(); finishRestore(Restore.Unavailable) }
            }
            override fun onBillingServiceDisconnected() {
                connecting = false; status = Status.Unavailable; changed(); finishRestore(Restore.Unavailable)
            }
        })
    }

    private fun queryProduct() {
        val request = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT)
            .setProductType(BillingClient.ProductType.INAPP).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(request)).build()) { result, found ->
            if (!closed) {
                product = if (result.responseCode == BillingClient.BillingResponseCode.OK)
                    found.productDetailsList.firstOrNull { it.productId == PRODUCT } else null
                price = product?.oneTimePurchaseOfferDetails?.formattedPrice
                if (status != Status.Pending) status = if (product != null && price != null) Status.Ready else Status.Unavailable
                changed()
            }
        }
    }

    fun restore(callback: (Restore) -> Unit) {
        callbacks += callback
        if (client.isReady) refresh() else connect()
    }

    private fun refresh() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (!closed) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    handle(purchases, true)
                    finishRestore(if (owned) Restore.Restored else Restore.NotOwned)
                } else finishRestore(Restore.Unavailable)
            }
        }
    }

    fun buy(activity: Activity): Boolean {
        if (closed || owned || status != Status.Ready) return false
        val details = product ?: return false
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        details.oneTimePurchaseOfferDetails?.offerToken?.takeIf(String::isNotBlank)?.let(item::setOfferToken)
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(item.build())).build())
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (closed) return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty(), false)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> { status = Status.Unavailable; changed() }
        }
    }

    private fun handle(purchases: List<Purchase>, complete: Boolean) {
        val evidence = purchases.flatMap { purchase -> purchase.products.map { id -> PaymentEvidence(id,
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> PaymentState.Purchased
                Purchase.PurchaseState.PENDING -> PaymentState.Pending
                else -> PaymentState.Other
            }) } }
        val entitlement = reconcileEntitlement(owned, evidence, complete)
        owned = entitlement.owned
        prefs.edit().putBoolean("owned", owned).apply()
        status = if (entitlement.pending) Status.Pending else if (product != null) Status.Ready else Status.Unavailable
        purchases.filter { PRODUCT in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { acknowledge(it.purchaseToken, 0) }
        changed()
    }

    private fun acknowledge(token: String, attempt: Int) {
        if (closed || !client.isReady || !acknowledging.add(token)) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
            handler.post {
                acknowledging.remove(token)
                if (!closed && result.responseCode !in listOf(BillingClient.BillingResponseCode.OK,
                        BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR) && attempt < 3)
                    handler.postDelayed({ acknowledge(token, attempt + 1) }, 2000L shl attempt)
            }
        }
    }

    private fun changed() { handler.post { if (!closed) listeners.toList().forEach { it() } } }
    private fun finishRestore(result: Restore) { handler.post {
        val pending = callbacks.toList(); callbacks.clear(); if (!closed) pending.forEach { it(result) }
    } }
    fun close() { closed = true; handler.removeCallbacksAndMessages(null); listeners.clear(); callbacks.clear(); client.endConnection() }

    companion object {
        const val PRODUCT = "remove_ads"
        const val PREFS = "streetgym_billing"
        fun noAds(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("owned", false)
    }
}
