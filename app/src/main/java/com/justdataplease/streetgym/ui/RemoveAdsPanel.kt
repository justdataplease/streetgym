package com.justdataplease.streetgym.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import com.justdataplease.streetgym.billing.RemoveAdsBilling

class RemoveAdsPanel(private val activity: Activity, private val billing: RemoveAdsBilling) : LinearLayout(activity) {
    private val heading = activity.titleText("Remove ads", 20f)
    private val note = activity.bodyText("One payment. No subscription.", 14f)
    private val message = activity.bodyText("", 13f)
    private val buy = activity.primaryButton("Remove ads · 9.99") {
        if (!billing.buy(activity)) showMessage("Google Play purchases are unavailable. Try again later.")
    }
    private val restore = activity.secondaryButton("Restore purchase") {
        showMessage("Checking purchase…")
        billing.restore { result -> showMessage(when (result) {
            RemoveAdsBilling.Restore.Restored -> "Purchase restored. Ads are off."
            RemoveAdsBilling.Restore.NotOwned -> "No purchase found for this Google account."
            RemoveAdsBilling.Restore.Unavailable -> "Couldn’t reach Google Play. Try again later."
        }) }
    }
    private val changed: () -> Unit = { render() }
    init {
        orientation = VERTICAL
        setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16))
        background = roundedDrawable(AppColors.SURFACE, activity.dp(24).toFloat(), AppColors.DIVIDER, activity.dp(1))
        addView(heading); addWithMargins(note, topDp = 6); addWithMargins(buy, topDp = 16)
        addWithMargins(restore, topDp = 8); addWithMargins(message, topDp = 8)
        render()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); billing.listeners += changed; billing.connect(); render() }
    override fun onDetachedFromWindow() { billing.listeners -= changed; super.onDetachedFromWindow() }
    private fun showMessage(value: String) {
        message.text = value
        message.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
    }
    private fun render() {
        heading.text = if (billing.owned) "Ads removed" else "Remove ads"
        note.text = if (billing.owned) "Enjoy every workout without ads." else "One payment. No subscription."
        buy.visibility = if (billing.owned) View.GONE else View.VISIBLE
        buy.text = "Remove ads · ${billing.price ?: "9.99"}"
        buy.isEnabled = !billing.owned && billing.price != null && billing.status == RemoveAdsBilling.Status.Ready
        buy.alpha = if (buy.isEnabled) 1f else .45f
        message.text = when {
            billing.owned -> ""
            billing.status == RemoveAdsBilling.Status.Pending -> "Payment pending. Ads stop when it’s confirmed."
            billing.status == RemoveAdsBilling.Status.Unavailable -> "Google Play purchases are unavailable."
            billing.status == RemoveAdsBilling.Status.Loading -> "Connecting to Google Play…"
            else -> ""
        }
        message.visibility = if (message.text.isEmpty()) View.GONE else View.VISIBLE
    }
}
