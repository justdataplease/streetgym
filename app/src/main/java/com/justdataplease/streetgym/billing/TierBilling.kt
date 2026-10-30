package com.justdataplease.streetgym.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*

/**
 * Plus (one non-consumable product) and Pro (a yearly subscription, or Pro for life: a second non-consumable product),
 * adapted from the Toolbox Supporter integration.
 * Ownership is cached in [Entitlement.PREFS]; only Play's formatted prices are ever shown.
 */
class TierBilling(context: Context) : PurchasesUpdatedListener {
    enum class Status { Loading, Ready, Unavailable }
    enum class Restore { Pro, Plus, NotOwned, Unavailable }
    private val prefs = Entitlement.prefs(context)
    /** The cached ownership as it is now: a restore, another screen or a test may have changed it since this one opened. */
    val plusOwned get() = prefs.getBoolean(Entitlement.PLUS_OWNED, false)
    val proActive get() = prefs.getBoolean(Entitlement.PRO_ACTIVE, false)
    val proLifeOwned get() = prefs.getBoolean(Entitlement.PRO_LIFE_OWNED, false)
    var plusPending = false; private set
    var proPending = false; private set
    var proLifePending = false; private set
    var plusPrice: String? = null; private set
    var proPrice: String? = null; private set
    var proLifePrice: String? = null; private set
    var status = Status.Loading; private set
    val listeners = mutableSetOf<() -> Unit>()
    private var plusDetails: ProductDetails? = null
    private var proDetails: ProductDetails? = null
    private var proLifeDetails: ProductDetails? = null
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
        if (client.isReady) { queryProducts(); refresh(); return }
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (closed) return
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) { queryProducts(); refresh() }
                else { status = Status.Unavailable; changed(); finishRestore(Restore.Unavailable) }
            }
            override fun onBillingServiceDisconnected() {
                connecting = false; status = Status.Unavailable; changed(); finishRestore(Restore.Unavailable)
            }
        })
    }

    private fun queryProducts() {
        query(PLUS, BillingClient.ProductType.INAPP) { details ->
            plusDetails = details
            plusPrice = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
        query(PRO, BillingClient.ProductType.SUBS) { details ->
            proDetails = details
            proPrice = details?.let(::proOffer)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        }
        query(PRO_LIFE, BillingClient.ProductType.INAPP) { details ->
            proLifeDetails = details
            proLifePrice = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    private fun query(id: String, type: String, found: (ProductDetails?) -> Unit) {
        val request = QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(request)).build()) { result, products ->
            handler.post {
                if (closed) return@post
                found(if (result.responseCode == BillingClient.BillingResponseCode.OK)
                    products.productDetailsList.firstOrNull { it.productId == id } else null)
                status = if (plusPrice != null || proPrice != null || proLifePrice != null) Status.Ready else Status.Unavailable
                changed()
            }
        }
    }

    /** The yearly base plan's offer: its plain base offer when Play lists one, else the first offer on it. */
    private fun proOffer(details: ProductDetails): ProductDetails.SubscriptionOfferDetails? =
        details.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == PRO_BASE_PLAN }
            .let { offers -> offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() }

    fun restore(callback: (Restore) -> Unit) {
        callbacks += callback
        if (client.isReady) refresh() else connect()
    }

    /** Both purchase lists; ownership of a product is revoked only by a successful query of its own type. */
    private fun refresh() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { inappResult, inapp ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { subsResult, subs ->
                handler.post {
                    if (closed) return@post
                    val inappOk = inappResult.responseCode == BillingClient.BillingResponseCode.OK
                    val subsOk = subsResult.responseCode == BillingClient.BillingResponseCode.OK
                    handle((if (inappOk) inapp else emptyList()) + (if (subsOk) subs else emptyList()),
                        complete = setOfNotNull(PLUS.takeIf { inappOk }, PRO_LIFE.takeIf { inappOk }, PRO.takeIf { subsOk }))
                    finishRestore(when {
                        !inappOk || !subsOk -> Restore.Unavailable
                        proActive || proLifeOwned -> Restore.Pro
                        plusOwned -> Restore.Plus
                        else -> Restore.NotOwned
                    })
                }
            }
        }
    }

    fun buyPlus(activity: Activity): Boolean = buyOnce(activity, plusDetails, plusOwned)

    fun buyProLife(activity: Activity): Boolean = buyOnce(activity, proLifeDetails, proLifeOwned)

    private fun buyOnce(activity: Activity, details: ProductDetails?, owned: Boolean): Boolean {
        if (details == null || closed || owned || status != Status.Ready) return false
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        details.oneTimePurchaseOfferDetails?.offerToken?.takeIf(String::isNotBlank)?.let(item::setOfferToken)
        return launch(activity, item.build())
    }

    fun buyPro(activity: Activity): Boolean {
        val details = proDetails ?: return false
        val offer = proOffer(details) ?: return false
        if (closed || proActive || status != Status.Ready) return false
        return launch(activity, BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
            .setOfferToken(offer.offerToken).build())
    }

    private fun launch(activity: Activity, item: BillingFlowParams.ProductDetailsParams): Boolean =
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item)).build())
            .responseCode == BillingClient.BillingResponseCode.OK

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (closed) return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handler.post { if (!closed) handle(purchases.orEmpty(), complete = emptySet()) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> handler.post { if (!closed) { status = Status.Unavailable; changed() } }
        }
    }

    private fun handle(purchases: List<Purchase>, complete: Set<String>) {
        val evidence = purchases.flatMap { purchase -> purchase.products.map { id -> PaymentEvidence(id,
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> PaymentState.Purchased
                Purchase.PurchaseState.PENDING -> PaymentState.Pending
                else -> PaymentState.Other
            }) } }
        val cachedPlus = plusOwned
        val cachedPro = proActive
        val cachedProLife = proLifeOwned
        val plus = reconcileEntitlement(PLUS, cachedPlus, evidence, PLUS in complete)
        val pro = reconcileEntitlement(PRO, cachedPro, evidence, PRO in complete)
        val proLife = reconcileEntitlement(PRO_LIFE, cachedProLife, evidence, PRO_LIFE in complete)
        plusPending = plus.pending
        proPending = pro.pending
        proLifePending = proLife.pending
        // Only what Play's answer decided is written (a confirmed purchase, or a complete query that revokes);
        // never a remembered value back over a newer one.
        if (plus.owned != cachedPlus || pro.owned != cachedPro || proLife.owned != cachedProLife) prefs.edit().apply {
            if (plus.owned != cachedPlus) putBoolean(Entitlement.PLUS_OWNED, plus.owned)
            if (pro.owned != cachedPro) putBoolean(Entitlement.PRO_ACTIVE, pro.owned)
            if (proLife.owned != cachedProLife) putBoolean(Entitlement.PRO_LIFE_OWNED, proLife.owned)
        }.apply()
        purchases.filter { it.products.any(PRODUCTS::contains) &&
            it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
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
        const val PLUS = "plus"
        const val PRO = "pro"
        const val PRO_BASE_PLAN = "yearly"
        const val PRO_LIFE = "pro_life"
        private val PRODUCTS = setOf(PLUS, PRO, PRO_LIFE)

        /** Play's own page for cancelling or changing the Pro subscription. */
        fun manageProUrl(packageName: String) =
            "https://play.google.com/store/account/subscriptions?sku=$PRO&package=$packageName"
    }
}
