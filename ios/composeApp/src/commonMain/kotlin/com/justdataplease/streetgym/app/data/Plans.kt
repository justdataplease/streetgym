package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.billing.tierFor

/**
 * The athlete's plan: what they bought, cached on the phone so the app opens in the right tier offline, and the
 * [Tier] every screen reads from it (Android: billing/Entitlement.kt and TierBilling.handle). The cache is kept under
 * Android's names ("streetgym_billing" › plus_owned, pro_active, pro_life_owned, simulated_tier), as text: "true" or
 * "false", the tier by its name.
 *
 * Ownership changes only on the store's answer: a confirmed purchase grants it; a complete answer without it (a refund,
 * a lapsed yearly Pro) takes it away; pending grants nothing. Nothing else is deleted when a plan ends.
 *
 * [purchases] is the store. [canSimulate]: a debug build (Swift's DEBUG) or the desktop, which offer Simulate tier in
 * the developer settings (Android: Settings › Simulate tier, debug builds only). The iPhone's debug builds can buy
 * through the Simulator's StoreKit file, so unlike Android's they aren't Pro by default: Default is what was bought.
 * [floor]: the lowest tier whatever is bought, Free in the app; previews and tests pass the tier they show.
 */
@Stable
class Plans(
    private val store: KeyValueStore = MemoryStore(),
    val purchases: Store = Store.None,
    val canSimulate: Boolean = false,
    private val floor: Tier = Tier.FREE,
) {
    var plusOwned by mutableStateOf(flag(PLUS_OWNED))
        private set

    /** The yearly Pro runs (Android: pro_active). */
    var proActive by mutableStateOf(flag(PRO_ACTIVE))
        private set

    var proLifeOwned by mutableStateOf(flag(PRO_LIFE_OWNED))
        private set

    var plusPending by mutableStateOf(false)
        private set
    var proPending by mutableStateOf(false)
        private set
    var proLifePending by mutableStateOf(false)
        private set

    /** Developer settings › Simulate tier, null for Default; kept only where it may be offered. */
    var simulated: Tier? by mutableStateOf(if (canSimulate) Tier.fromName(store.get(SIMULATED_TIER)) else null)
        private set

    /**
     * The tier from the cached ownership, raised by a simulated tier where that is offered (Android: Entitlement.tier
     * through the engine's tierFor; a simulated Free or Plus never lowers what was bought).
     */
    val tier: Tier
        get() = maxOf(floor, tierFor(plusOwned, proActive || proLifeOwned, debug = simulated != null, simulated = simulated))

    /** Plus and Pro see no ads (Android: Entitlement.adFree). */
    val adFree: Boolean get() = tier.adFree

    /** Simulate tier's pick: [tier], or null for Default (Android: Entitlement.setSimulatedTier). */
    fun simulate(tier: Tier?) {
        if (!canSimulate) return
        simulated = tier
        store.set(SIMULATED_TIER, tier?.name)
    }

    /** Asks the store for prices and purchases again (Android: billing.connect on resume and when the plans show). */
    fun connect() = purchases.connect()

    /** Follows the store's answers while the app runs: a purchase, a refund, a lapse, Ask to Buy approved later. */
    suspend fun follow() {
        purchases.entitlements.collect { answer -> answer?.let(::reconcile) }
    }

    /**
     * The store's answer applied to the cache (Android: TierBilling.handle): only what the answer decided is written,
     * never a remembered value back over a newer one.
     */
    fun reconcile(answer: StoreEntitlements) {
        val evidence = answer.owned.map { PaymentEvidence(it, PaymentState.Purchased) } +
            answer.pending.map { PaymentEvidence(it, PaymentState.Pending) }
        val plus = reconcileEntitlement(Products.PLUS, plusOwned, evidence, answer.complete)
        val pro = reconcileEntitlement(Products.PRO, proActive, evidence, answer.complete)
        val proLife = reconcileEntitlement(Products.PRO_LIFE, proLifeOwned, evidence, answer.complete)
        plusPending = plus.pending
        proPending = pro.pending
        proLifePending = proLife.pending
        if (plus.owned != plusOwned) {
            plusOwned = plus.owned
            store.set(PLUS_OWNED, plus.owned.toString())
        }
        if (pro.owned != proActive) {
            proActive = pro.owned
            store.set(PRO_ACTIVE, pro.owned.toString())
        }
        if (proLife.owned != proLifeOwned) {
            proLifeOwned = proLife.owned
            store.set(PRO_LIFE_OWNED, proLife.owned.toString())
        }
    }

    /** Buys [product]; [done] hears how it ended once the plan shows it (Android: buyPlus, buyProLife, buyPro). */
    fun buy(product: String, done: (Bought) -> Unit = {}) {
        purchases.buy(product) { result ->
            purchases.entitlements.value?.let(::reconcile)
            done(result)
        }
    }

    /**
     * Restore purchases (Android: TierBilling.restore): the store asked again, then Pro, Plus or nothing found, or that
     * it couldn't be reached.
     */
    fun restore(done: (Restored) -> Unit) {
        purchases.restore { reached ->
            purchases.entitlements.value?.let(::reconcile)
            done(when {
                !reached -> Restored.UNAVAILABLE
                proActive || proLifeOwned -> Restored.PRO
                plusOwned -> Restored.PLUS
                else -> Restored.NOT_OWNED
            })
        }
    }

    private fun flag(key: String): Boolean = store.get(key)?.toBooleanStrictOrNull() ?: false

    companion object {
        const val PLUS_OWNED = "streetgym_billing.plus_owned"
        const val PRO_ACTIVE = "streetgym_billing.pro_active"
        const val PRO_LIFE_OWNED = "streetgym_billing.pro_life_owned"
        const val SIMULATED_TIER = "streetgym_billing.simulated_tier"
    }
}
