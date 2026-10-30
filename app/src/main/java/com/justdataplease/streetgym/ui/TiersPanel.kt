package com.justdataplease.streetgym.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.billing.TierBilling

/** What the athlete tried to open; null for the Settings panel. */
sealed interface Paywall {
    data class Level(val number: Int) : Paywall
    data object Parks : Paywall
    data object Rename : Paywall
    data object Leaderboard : Paywall
    /** The ready workouts in Training › Library (Pro). */
    data object Ready : Paywall
    data class Saved(val limit: Int) : Paywall
    /** Own lists in My workouts: Plus, or more of them with Pro once Plus has made all of its [limit]. */
    data class Lists(val limit: Int) : Paywall
}

/** The Plus and Pro offers: in Settings, or as the card that replaces a locked feature. */
class TiersPanel(
    private val activity: Activity,
    private val billing: TierBilling,
    private val paywall: Paywall? = null,
) : LinearLayout(activity) {
    /** Only Pro opens it: the ready workouts, and Levels 7 to 10. */
    private val proOnly = paywall == Paywall.Ready || paywall is Paywall.Level && LevelAccess.tierFor(paywall.number) == Tier.PRO
    private val heading = activity.titleText(activity.getString(when (paywall) {
        null -> R.string.tiers_title
        is Paywall.Level -> if (proOnly) R.string.paywall_level_pro else R.string.paywall_level
        Paywall.Parks -> R.string.paywall_parks
        Paywall.Rename -> R.string.paywall_rename
        Paywall.Leaderboard -> R.string.paywall_leaderboard
        Paywall.Ready -> R.string.paywall_ready
        is Paywall.Saved -> R.string.paywall_saved
        is Paywall.Lists -> if (paywall.limit == 0) R.string.paywall_lists else R.string.paywall_lists_full
    }, (paywall as? Paywall.Level)?.number ?: (paywall as? Paywall.Saved)?.limit ?: (paywall as? Paywall.Lists)?.limit ?: 0), 20f)
    private val current = activity.bodyText("", 14f)
    private val plusBlock = block()
    private val plusNote = activity.bodyText("", 13f)
    private val plusBuy = activity.primaryButton(activity.getString(R.string.plus_buy)) {
        if (!billing.buyPlus(activity)) showMessage(activity.getString(R.string.purchase_unavailable_retry))
    }
    private val proBlock = block()
    private val proNote = activity.bodyText("", 13f)
    private val proBuy = activity.primaryButton(activity.getString(R.string.pro_buy)) {
        if (!billing.buyPro(activity)) showMessage(activity.getString(R.string.purchase_unavailable_retry))
    }
    private val proLifeBuy = activity.secondaryButton(activity.getString(R.string.pro_life_buy)) {
        if (!billing.buyProLife(activity)) showMessage(activity.getString(R.string.purchase_unavailable_retry))
    }
    private val proManage = activity.secondaryButton(activity.getString(R.string.pro_manage)) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TierBilling.manageProUrl(activity.packageName)))) }
    }
    /** What the yearly price pays for, and that Pro for life is one payment. */
    private val proWhy = activity.bodyText(activity.getString(R.string.pro_why), 12f)
    private val message = activity.bodyText("", 13f)
    private val restore = activity.secondaryButton(activity.getString(R.string.restore_purchase)) {
        showMessage(activity.getString(R.string.purchase_checking))
        billing.restore { result -> showMessage(activity.getString(when (result) {
            TierBilling.Restore.Pro -> R.string.purchase_restored_pro
            TierBilling.Restore.Plus -> R.string.purchase_restored_plus
            TierBilling.Restore.NotOwned -> R.string.purchase_not_found
            TierBilling.Restore.Unavailable -> R.string.purchase_unreachable
        })) }
    }
    private val changed: () -> Unit = { render() }

    init {
        orientation = VERTICAL
        setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16))
        background = roundedDrawable(AppColors.SURFACE, activity.dp(24).toFloat(), AppColors.DIVIDER, activity.dp(1))
        // Past Plus, a paywall offers only Pro.
        if (paywall != null) addWithMargins(activity.lockBadge(activity.getString(
            if (proOnly || Entitlement.tier(activity) >= Tier.PLUS) R.string.tier_pro else R.string.tier_plus)),
            width = ViewGroup.LayoutParams.WRAP_CONTENT, bottomDp = 10)
        addView(heading)
        if (paywall is Paywall.Level) addWithMargins(activity.bodyText(activity.getString(
            if (proOnly) R.string.paywall_level_pro_body else R.string.paywall_level_body), 14f), topDp = 6)
        if (paywall == Paywall.Ready) addWithMargins(activity.bodyText(activity.getString(R.string.paywall_ready_body), 14f), topDp = 6)
        addWithMargins(current, topDp = 6)
        plusBlock.addView(activity.titleText(activity.getString(R.string.plus_title), 17f))
        plusBlock.addWithMargins(features(R.array.plus_features), topDp = 6)
        plusBlock.addWithMargins(plusNote, topDp = 6)
        plusBlock.addWithMargins(plusBuy, topDp = 12)
        addWithMargins(plusBlock, topDp = 14)
        proBlock.addView(activity.titleText(activity.getString(R.string.pro_title), 17f))
        proBlock.addWithMargins(features(R.array.pro_features), topDp = 6)
        proBlock.addWithMargins(proNote, topDp = 6)
        proBlock.addWithMargins(proBuy, topDp = 12)
        proBlock.addWithMargins(proLifeBuy, topDp = 8)
        proBlock.addWithMargins(proManage, topDp = 8)
        addWithMargins(proBlock, topDp = 12)
        addWithMargins(proWhy, topDp = 8)
        addWithMargins(restore, topDp = 12)
        addWithMargins(message, topDp = 8)
        render()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); billing.listeners += changed; billing.connect(); render() }
    override fun onDetachedFromWindow() { billing.listeners -= changed; super.onDetachedFromWindow() }

    private fun block() = LinearLayout(activity).apply {
        orientation = VERTICAL
        setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
        background = roundedDrawable(AppColors.INFO_SURFACE, activity.dp(18).toFloat())
    }

    /** What a plan includes, one bullet a line. */
    private fun features(items: Int) = LinearLayout(activity).apply {
        orientation = VERTICAL
        activity.resources.getStringArray(items).forEachIndexed { index, item ->
            addWithMargins(LinearLayout(activity).apply {
                orientation = HORIZONTAL
                addView(activity.bodyText("•", 14f).apply { setTextColor(AppColors.PRIMARY); typeface = AppType.bold },
                    LayoutParams(activity.dp(16), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(activity.bodyText(item, 14f).apply { setTextColor(AppColors.TEXT) },
                    LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }, topDp = if (index == 0) 0 else 4)
        }
    }

    private fun showMessage(value: String) {
        message.text = value
        message.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
    }

    fun render() {
        val tier = Entitlement.tier(activity)
        current.text = activity.getString(when (tier) {
            Tier.FREE -> R.string.tier_current_free
            Tier.PLUS -> R.string.tier_current_plus
            Tier.PRO -> R.string.tier_current_pro
        })
        current.visibility = if (paywall == null) View.VISIBLE else View.GONE
        // A paywall offers only what unlocks the feature; Settings always shows both, with what's owned.
        plusBlock.visibility = if (proOnly || paywall != null && tier >= Tier.PLUS) View.GONE else View.VISIBLE
        proBlock.visibility = if (paywall != null && tier == Tier.PRO) View.GONE else View.VISIBLE
        proWhy.visibility = proBlock.visibility
        val ready = billing.status == TierBilling.Status.Ready
        // Only Play's formatted price is ever shown; until it arrives the buttons stay disabled.
        plusBuy.visibility = if (tier >= Tier.PLUS) View.GONE else View.VISIBLE
        plusBuy.text = billing.plusPrice?.let { activity.getString(R.string.plus_buy_price, it) } ?: activity.getString(R.string.plus_buy)
        plusBuy.isEnabled = tier < Tier.PLUS && ready && billing.plusPrice != null
        plusBuy.alpha = if (plusBuy.isEnabled) 1f else .45f
        plusNote.text = when {
            tier >= Tier.PLUS -> activity.getString(R.string.plus_owned)
            billing.plusPending -> activity.getString(R.string.purchase_pending)
            else -> ""
        }
        plusNote.visibility = if (plusNote.text.isEmpty()) View.GONE else View.VISIBLE
        proBuy.visibility = if (tier == Tier.PRO) View.GONE else View.VISIBLE
        proBuy.text = billing.proPrice?.let { activity.getString(R.string.pro_buy_price, it) } ?: activity.getString(R.string.pro_buy)
        proBuy.isEnabled = tier < Tier.PRO && ready && billing.proPrice != null
        proBuy.alpha = if (proBuy.isEnabled) 1f else .45f
        proLifeBuy.visibility = proBuy.visibility
        proLifeBuy.text = billing.proLifePrice?.let { activity.getString(R.string.pro_life_buy_price, it) }
            ?: activity.getString(R.string.pro_life_buy)
        proLifeBuy.isEnabled = tier < Tier.PRO && ready && billing.proLifePrice != null
        proLifeBuy.alpha = if (proLifeBuy.isEnabled) 1f else .45f
        proManage.visibility = if (billing.proActive) View.VISIBLE else View.GONE
        proNote.text = when {
            billing.proLifeOwned -> activity.getString(R.string.pro_life_owned)
            tier == Tier.PRO -> activity.getString(R.string.pro_active)
            billing.proPending || billing.proLifePending -> activity.getString(R.string.purchase_pending)
            else -> ""
        }
        proNote.visibility = if (proNote.text.isEmpty()) View.GONE else View.VISIBLE
        showMessage(when {
            tier == Tier.PRO -> ""
            billing.status == TierBilling.Status.Unavailable -> activity.getString(R.string.purchase_unavailable)
            billing.status == TierBilling.Status.Loading -> activity.getString(R.string.purchase_connecting)
            else -> ""
        })
    }
}
