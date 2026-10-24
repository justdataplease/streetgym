package com.justdataplease.streetgym.billing

enum class PaymentState { Purchased, Pending, Other }
data class PaymentEvidence(val product: String, val state: PaymentState)
data class AdEntitlement(val owned: Boolean, val pending: Boolean)

/** Only confirmed purchases grant access; only a successful complete query can revoke it. */
fun reconcileEntitlement(previouslyOwned: Boolean, purchases: List<PaymentEvidence>, complete: Boolean): AdEntitlement {
    val ours = purchases.filter { it.product == "remove_ads" }
    val paid = ours.any { it.state == PaymentState.Purchased }
    val owned = if (complete || paid) paid else previouslyOwned
    return AdEntitlement(owned, !owned && ours.any { it.state == PaymentState.Pending })
}
