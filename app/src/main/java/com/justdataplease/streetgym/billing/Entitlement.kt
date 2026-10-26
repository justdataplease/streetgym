package com.justdataplease.streetgym.billing

import android.content.Context
import com.justdataplease.streetgym.BuildConfig

enum class PaymentState { Purchased, Pending, Other }
data class PaymentEvidence(val product: String, val state: PaymentState)
data class Ownership(val owned: Boolean, val pending: Boolean)

/** Only confirmed purchases grant access; only a successful complete query can revoke it. */
fun reconcileEntitlement(product: String, previouslyOwned: Boolean, purchases: List<PaymentEvidence>, complete: Boolean): Ownership {
    val ours = purchases.filter { it.product == product }
    val paid = ours.any { it.state == PaymentState.Purchased }
    val owned = if (complete || paid) paid else previouslyOwned
    return Ownership(owned, !owned && ours.any { it.state == PaymentState.Pending })
}

/** Cached ownership. Every key in [PREFS] is an entitlement key, so listeners can react to any change. */
object Entitlement {
    const val PREFS = "streetgym_billing"
    const val PLUS_OWNED = "plus_owned"
    const val PRO_ACTIVE = "pro_active"
    private const val SIMULATED_TIER = "simulated_tier"

    fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun tier(context: Context): Tier = prefs(context).let {
        tierFor(it.getBoolean(PLUS_OWNED, false), it.getBoolean(PRO_ACTIVE, false), BuildConfig.DEBUG, simulatedTier(context))
    }

    /** Every level, from Plus up. */
    fun allLevels(context: Context): Boolean = tier(context).allLevels

    fun adFree(context: Context): Boolean = tier(context).adFree

    fun simulatedTier(context: Context): Tier? =
        if (BuildConfig.DEBUG) Tier.fromName(prefs(context).getString(SIMULATED_TIER, null)) else null

    fun setSimulatedTier(context: Context, tier: Tier?) {
        if (BuildConfig.DEBUG) prefs(context).edit().putString(SIMULATED_TIER, tier?.name).apply()
    }
}
