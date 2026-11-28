package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Bought
import com.justdataplease.streetgym.app.data.GoogleAnalytics
import com.justdataplease.streetgym.app.data.Offers
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.data.Products
import com.justdataplease.streetgym.app.data.analyticsSource
import com.justdataplease.streetgym.app.data.Restored
import com.justdataplease.streetgym.app.data.StoreStatus
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.paywall_ads
import com.justdataplease.streetgym.app.resources.paywall_ads_body
import com.justdataplease.streetgym.app.resources.paywall_custom
import com.justdataplease.streetgym.app.resources.paywall_leaderboard
import com.justdataplease.streetgym.app.resources.paywall_level
import com.justdataplease.streetgym.app.resources.paywall_level_body
import com.justdataplease.streetgym.app.resources.paywall_level_pro
import com.justdataplease.streetgym.app.resources.paywall_level_pro_body
import com.justdataplease.streetgym.app.resources.paywall_lists
import com.justdataplease.streetgym.app.resources.paywall_lists_full
import com.justdataplease.streetgym.app.resources.paywall_parks
import com.justdataplease.streetgym.app.resources.paywall_ready
import com.justdataplease.streetgym.app.resources.paywall_ready_body
import com.justdataplease.streetgym.app.resources.paywall_saved
import com.justdataplease.streetgym.app.resources.plan_once
import com.justdataplease.streetgym.app.resources.plus_buy
import com.justdataplease.streetgym.app.resources.plus_features
import com.justdataplease.streetgym.app.resources.plus_owned
import com.justdataplease.streetgym.app.resources.plus_title
import com.justdataplease.streetgym.app.resources.privacy_policy
import com.justdataplease.streetgym.app.resources.pro_active
import com.justdataplease.streetgym.app.resources.pro_features
import com.justdataplease.streetgym.app.resources.pro_life_buy
import com.justdataplease.streetgym.app.resources.pro_life_owned
import com.justdataplease.streetgym.app.resources.pro_manage
import com.justdataplease.streetgym.app.resources.pro_title
import com.justdataplease.streetgym.app.resources.pro_yearly_buy
import com.justdataplease.streetgym.app.resources.pro_yearly_buy_price
import com.justdataplease.streetgym.app.resources.promo_redeem
import com.justdataplease.streetgym.app.resources.purchase_checking
import com.justdataplease.streetgym.app.resources.purchase_restored_plus
import com.justdataplease.streetgym.app.resources.purchase_restored_pro
import com.justdataplease.streetgym.app.resources.restore_purchase
import com.justdataplease.streetgym.app.resources.simulate_tier
import com.justdataplease.streetgym.app.resources.simulate_tier_default
import com.justdataplease.streetgym.app.resources.simulate_tier_note_ios
import com.justdataplease.streetgym.app.resources.store_connecting
import com.justdataplease.streetgym.app.resources.store_not_found
import com.justdataplease.streetgym.app.resources.store_pending
import com.justdataplease.streetgym.app.resources.store_unavailable
import com.justdataplease.streetgym.app.resources.store_unavailable_retry
import com.justdataplease.streetgym.app.resources.store_unreachable
import com.justdataplease.streetgym.app.resources.store_yearly_note
import com.justdataplease.streetgym.app.resources.terms_of_use
import com.justdataplease.streetgym.app.resources.tier_current_free
import com.justdataplease.streetgym.app.resources.tier_current_plus
import com.justdataplease.streetgym.app.resources.tier_current_pro
import com.justdataplease.streetgym.app.resources.tier_free
import com.justdataplease.streetgym.app.resources.tier_plus
import com.justdataplease.streetgym.app.resources.tier_pro
import com.justdataplease.streetgym.app.resources.tiers_title
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.billing.Tier
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/*
 * Plus and Pro (android-streetgym, ui/TiersPanel.kt and HomeActivity.showPaywall): the plans panel in Settings, and the
 * offer every locked thing opens, with only the plans that unlock it. Prices are only ever the store's own.
 */

/** Apple's standard licence agreement, which the App Store applies when an app has no terms of its own (Apple 3.1.2). */
const val TERMS_URL = "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"

/** Street Gym's privacy policy, as the Play listing links it (docs/play/streetgymnastics/listing.md). */
const val PRIVACY_URL = "https://streetgym.app/privacy"

/**
 * The offer for what [paywall] locks (Android: showPaywall): the plans panel in a dialog, with Cancel. It stays up after a
 * purchase, as Android's does, showing what is left to buy.
 */
@Composable
fun OfferDialog(model: AppModel, paywall: Paywall, onClose: () -> Unit) {
    // Google Analytics' paywall_view, once each time the offer opens (Android: showPaywall).
    LaunchedEffect(model, paywall) { model.offerShown(paywall) }
    StreetDialog(title = null, onDismiss = onClose, confirm = DialogAction(stringResource(Res.string.cancel), onClose)) {
        PlansPanel(model, paywall, Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp))
    }
}

/** An offer's heading (Android: TiersPanel's heading). */
@Composable
fun offerHeading(paywall: Paywall?): String = when (paywall) {
    null -> stringResource(Res.string.tiers_title)
    is Paywall.Level -> stringResource(if (Offers.proOnly(paywall)) Res.string.paywall_level_pro else Res.string.paywall_level, paywall.number)
    Paywall.Ready -> stringResource(Res.string.paywall_ready)
    Paywall.Parks -> stringResource(Res.string.paywall_parks)
    Paywall.Leaderboard -> stringResource(Res.string.paywall_leaderboard)
    is Paywall.Saved -> stringResource(Res.string.paywall_saved, paywall.limit)
    Paywall.Custom -> stringResource(Res.string.paywall_custom)
    is Paywall.Lists -> if (paywall.limit == 0) stringResource(Res.string.paywall_lists)
        else stringResource(Res.string.paywall_lists_full, paywall.limit)
    Paywall.Ads -> stringResource(Res.string.paywall_ads)
}

/** The line under an offer's heading, where Android has one: a level's, the ready workouts', Remove ads'. */
@Composable
private fun offerBody(paywall: Paywall?): String? = when (paywall) {
    is Paywall.Level -> stringResource(if (Offers.proOnly(paywall)) Res.string.paywall_level_pro_body else Res.string.paywall_level_body)
    Paywall.Ready -> stringResource(Res.string.paywall_ready_body)
    Paywall.Ads -> stringResource(Res.string.paywall_ads_body)
    else -> null
}

/**
 * The plans (Android: TiersPanel): in Settings ([paywall] null) the current plan and both plans with what is owned; in
 * an offer its tag, heading and line, and only the plans that unlock it. Each plan has the store's price ("one payment,
 * for life"), what it opens, and its button, which waits for the price; Pro for life is the main offer, the yearly plan
 * the smaller way in, with the renewal note, Apple's terms and the privacy policy, and Manage subscription while it runs.
 * Then Restore purchases, Redeem a promo code (Apple's offer-code sheet), and a line for what the store said.
 */
@Composable
fun PlansPanel(model: AppModel, paywall: Paywall?, modifier: Modifier = Modifier) {
    val theme = Street.theme
    val palette = theme.palette
    val plans = model.plans
    val store = plans.purchases
    val status by store.status.collectAsState()
    val prices by store.prices.collectAsState()
    val tier = plans.tier
    val links = LocalUriHandler.current
    // Android: onAttachedToWindow's connect. The store is asked again each time the plans show.
    LaunchedEffect(plans) { plans.connect() }
    // What the last action said; Android's render() puts the store's state there otherwise.
    var said by remember { mutableStateOf<String?>(null) }
    val checking = stringResource(Res.string.purchase_checking)
    val restoredPro = stringResource(Res.string.purchase_restored_pro)
    val restoredPlus = stringResource(Res.string.purchase_restored_plus)
    val notFound = stringResource(Res.string.store_not_found)
    val unreachable = stringResource(Res.string.store_unreachable)
    val retry = stringResource(Res.string.store_unavailable_retry)
    val buyEnded: (Bought) -> Unit = { result -> if (result == Bought.FAILED) said = retry }
    // A buy button tapped: Google Analytics' paywall_tap with what opened the offer, Settings' for its plans (Android:
    // TiersPanel's buttons), then the store.
    fun buy(product: String, plan: String) {
        model.googleAnalytics.paywallTap(paywall.analyticsSource, plan)
        plans.buy(product, buyEnded)
    }
    val ready = status == StoreStatus.READY
    val plusPrice = prices[Products.PLUS]
    val proLifePrice = prices[Products.PRO_LIFE]
    val proPrice = prices[Products.PRO]

    Column(modifier.fillMaxWidth().streetBackground(theme, palette.surface, 24.dp, palette.divider, 1.dp).padding(16.dp)) {
        // Past Plus, an offer offers only Pro. Remove ads locks nothing, so it has no tag.
        Offers.badge(paywall, tier)?.let { badge ->
            LockBadge(stringResource(if (badge == Tier.PRO) Res.string.tier_pro else Res.string.tier_plus), Modifier.padding(bottom = 10.dp))
        }
        TitleText(offerHeading(paywall), 20f)
        offerBody(paywall)?.let { BodyText(it, 14f, Modifier.padding(top = 6.dp)) }
        if (paywall == null) BodyText(
            stringResource(when (tier) {
                Tier.FREE -> Res.string.tier_current_free
                Tier.PLUS -> Res.string.tier_current_plus
                Tier.PRO -> Res.string.tier_current_pro
            }),
            14f, Modifier.padding(top = 6.dp),
        )

        if (Offers.showsPlus(paywall, tier)) PlanBlock(Modifier.padding(top = 14.dp), outlined = false) {
            PlanHeader(stringResource(Res.string.plus_title), plusPrice, owned = tier >= Tier.PLUS)
            Features(Res.array.plus_features, Modifier.padding(top = 10.dp))
            val note = when {
                tier >= Tier.PLUS -> stringResource(Res.string.plus_owned)
                plans.plusPending -> stringResource(Res.string.store_pending)
                else -> null
            }
            note?.let { BodyText(it, 13f, Modifier.padding(top = 6.dp)) }
            if (tier < Tier.PLUS) {
                val enabled = ready && plusPrice != null
                PrimaryButton(stringResource(Res.string.plus_buy), Modifier.padding(top = 12.dp).alpha(if (enabled) 1f else DISABLED),
                    enabled = enabled) { buy(Products.PLUS, GoogleAnalytics.PLAN_PLUS) }
            }
        }

        if (Offers.showsPro(paywall, tier)) PlanBlock(Modifier.padding(top = 12.dp), outlined = true) {
            PlanHeader(stringResource(Res.string.pro_title), proLifePrice, owned = tier == Tier.PRO)
            Features(Res.array.pro_features, Modifier.padding(top = 10.dp))
            val note = when {
                plans.proLifeOwned -> stringResource(Res.string.pro_life_owned)
                tier == Tier.PRO -> stringResource(Res.string.pro_active)
                plans.proPending || plans.proLifePending -> stringResource(Res.string.store_pending)
                else -> null
            }
            note?.let { BodyText(it, 13f, Modifier.padding(top = 6.dp)) }
            if (tier != Tier.PRO) {
                val lifeEnabled = ready && proLifePrice != null
                PrimaryButton(stringResource(Res.string.pro_life_buy), Modifier.padding(top = 12.dp).alpha(if (lifeEnabled) 1f else DISABLED),
                    enabled = lifeEnabled) { buy(Products.PRO_LIFE, GoogleAnalytics.PLAN_PRO_LIFE) }
                val yearlyEnabled = ready && proPrice != null
                SecondaryButton(
                    proPrice?.let { stringResource(Res.string.pro_yearly_buy_price, it) } ?: stringResource(Res.string.pro_yearly_buy),
                    Modifier.padding(top = 8.dp).alpha(if (yearlyEnabled) 1f else DISABLED),
                    enabled = yearlyEnabled,
                ) { buy(Products.PRO, GoogleAnalytics.PLAN_PRO_YEARLY) }
                BodyText(stringResource(Res.string.store_yearly_note), 12f, Modifier.fillMaxWidth().padding(top = 6.dp), align = TextAlign.Center)
            }
            // Apple 3.1.2: the subscription's terms and the privacy policy, beside it.
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center) {
                LinkText(stringResource(Res.string.terms_of_use)) { links.openUri(TERMS_URL) }
                BodyText("·", 12f, Modifier.padding(horizontal = 4.dp).align(Alignment.CenterVertically))
                LinkText(stringResource(Res.string.privacy_policy)) { links.openUri(PRIVACY_URL) }
            }
            if (plans.proActive) SecondaryButton(stringResource(Res.string.pro_manage), Modifier.padding(top = 8.dp)) { store.manageSubscription() }
        }

        SecondaryButton(stringResource(Res.string.restore_purchase), Modifier.padding(top = 12.dp)) {
            said = checking
            plans.restore { result ->
                said = when (result) {
                    Restored.PRO -> restoredPro
                    Restored.PLUS -> restoredPlus
                    Restored.NOT_OWNED -> notFound
                    Restored.UNAVAILABLE -> unreachable
                }
            }
        }
        // Android: Play's redeem page; the iPhone's is Apple's own sheet, and what it redeems arrives as a purchase.
        SecondaryButton(stringResource(Res.string.promo_redeem), Modifier.padding(top = 8.dp)) {
            said = null
            store.redeemCode { opened -> if (!opened) said = retry }
        }
        val message = said ?: when {
            tier == Tier.PRO -> null
            status == StoreStatus.UNAVAILABLE -> stringResource(Res.string.store_unavailable)
            status == StoreStatus.LOADING -> stringResource(Res.string.store_connecting)
            else -> null
        }
        message?.let { BodyText(it, 13f, Modifier.padding(top = 8.dp)) }
    }
}

/** A button the store can't take yet, as Android dims it. */
private const val DISABLED = 0.45f

/** A plan's box: the info colour, 18 dp corners in the theme's style, Pro's outlined in the action colour. */
@Composable
private fun PlanBlock(modifier: Modifier, outlined: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val theme = Street.theme
    Column(
        modifier.fillMaxWidth()
            .streetBackground(theme, theme.palette.infoSurface, 18.dp, if (outlined) theme.palette.action else null, if (outlined) 2.dp else 0.dp)
            .padding(14.dp),
        content = content,
    )
}

/**
 * A plan's name on the left; its price on the right with what that price is ("one payment, for life") under it. The
 * price waits for the store, and price and terms go once the plan is owned (Android: planHeader and showPrice).
 */
@Composable
private fun PlanHeader(name: String, price: String?, owned: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TitleText(name, 20f, Modifier.weight(1f))
        if (!owned) Column(horizontalAlignment = Alignment.End) {
            price?.let { TitleText(it, 24f, align = TextAlign.End) }
            BodyText(stringResource(Res.string.plan_once), 12f, align = TextAlign.End)
        }
    }
}

/** What a plan includes, one bullet a line (Android: features). */
@Composable
private fun Features(items: StringArrayResource, modifier: Modifier) {
    val palette = Street.colors
    Column(modifier.fillMaxWidth()) {
        stringArrayResource(items).forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 4.dp)) {
                BodyText("•", 14f, Modifier.width(16.dp), color = palette.primary, bold = true)
                BodyText(item, 14f, Modifier.weight(1f), color = palette.text)
            }
        }
    }
}

/** A link in words: small, underlined, in the theme's colour, at least 40 dp tall to tap. */
@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    BasicText(
        text,
        Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        style = TextStyle(color = Street.colors.primary, fontSize = 12.sp, fontFamily = Street.type.bold,
            textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center),
    )
}

/**
 * Developer settings › Simulate tier (Android: Settings' Simulate tier card, debug builds only): Default, Free, Plus or
 * Pro; a simulated tier only raises what was bought.
 */
@Composable
fun SimulateTier(model: AppModel, modifier: Modifier = Modifier) {
    val plans = model.plans
    val choices = listOf(null, Tier.FREE, Tier.PLUS, Tier.PRO)
    Column(modifier) {
        TitleText(stringResource(Res.string.simulate_tier), 17f)
        BodyText(stringResource(Res.string.simulate_tier_note_ios), 13f, Modifier.padding(top = 4.dp))
        SegmentedSwitch(
            listOf(Res.string.simulate_tier_default, Res.string.tier_free, Res.string.tier_plus, Res.string.tier_pro).map { stringResource(it) },
            choices.indexOf(plans.simulated),
            Modifier.padding(top = 10.dp),
            compact = true,
        ) { plans.simulate(choices[it]) }
    }
}
