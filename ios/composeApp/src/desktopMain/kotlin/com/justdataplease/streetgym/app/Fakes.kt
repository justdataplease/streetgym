package com.justdataplease.streetgym.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AdLook
import com.justdataplease.streetgym.app.data.AdPrivacy
import com.justdataplease.streetgym.app.data.Ads
import com.justdataplease.streetgym.app.data.Bought
import com.justdataplease.streetgym.app.data.Products
import com.justdataplease.streetgym.app.data.Store
import com.justdataplease.streetgym.app.data.StoreEntitlements
import com.justdataplease.streetgym.app.data.StoreStatus
import com.justdataplease.streetgym.app.theme.Street
import kotlinx.coroutines.flow.MutableStateFlow

/** Android's Play prices (docs/TIERS.md), as a store writes them in euros. */
val ANDROID_PRICES = mapOf(Products.PLUS to "€14.99", Products.PRO_LIFE to "€69.99", Products.PRO to "€29.99")

/**
 * A store on the desktop and in tests, in place of StoreKit: the products at Android's prices, and purchases that go
 * through, wait, or fail as [next] says. [reachable] false is a phone offline: nothing answers, so the cached plan
 * stays. [refund], [lapse] and [approve] are the store's own changes afterwards; [code] is what an offer code gives.
 */
class FakeStore(private val catalog: Map<String, String> = ANDROID_PRICES, var reachable: Boolean = true) : Store {
    override val status = MutableStateFlow(StoreStatus.LOADING)
    override val prices = MutableStateFlow<Map<String, String>>(emptyMap())
    override val entitlements = MutableStateFlow<StoreEntitlements?>(null)
    private val owned = mutableSetOf<String>()
    private val pending = mutableSetOf<String>()

    /** How the next purchase ends. */
    var next = Bought.BOUGHT

    /** Every product asked for, in order. */
    val asked = mutableListOf<String>()

    /** What redeeming an offer code gives; null redeems nothing. */
    var code: String? = null
    var redeemed = 0
        private set
    var managed = 0
        private set
    var connects = 0
        private set

    /** Products the store knows the athlete has, as at a first start. */
    fun owns(vararg products: String) { owned += products }

    override fun connect() {
        connects++
        if (!reachable) {
            if (prices.value.isEmpty()) status.value = StoreStatus.UNAVAILABLE
            return
        }
        prices.value = catalog
        status.value = if (catalog.isEmpty()) StoreStatus.UNAVAILABLE else StoreStatus.READY
        publish()
    }

    override fun buy(product: String, done: (Bought) -> Unit) {
        asked += product
        if (!reachable || product !in prices.value) return done(Bought.FAILED)
        when (next) {
            Bought.BOUGHT -> { owned += product; pending -= product }
            Bought.PENDING -> pending += product
            Bought.CANCELLED, Bought.FAILED -> Unit
        }
        publish()
        done(next)
    }

    override fun restore(done: (Boolean) -> Unit) {
        if (!reachable) return done(false)
        publish()
        done(true)
    }

    override fun redeemCode(done: (Boolean) -> Unit) {
        redeemed++
        if (!reachable) return done(false)
        code?.let { owned += it; publish() }
        done(true)
    }

    override fun manageSubscription() { managed++ }

    /** The store took [product] back: a refund. */
    fun refund(product: String) {
        owned -= product
        if (reachable) publish()
    }

    /** The yearly Pro ran out without renewing. */
    fun lapse() = refund(Products.PRO)

    /** A payment waiting (Ask to Buy) was approved. */
    fun approve(product: String) {
        pending -= product
        owned += product
        if (reachable) publish()
    }

    private fun publish() {
        entitlements.value = StoreEntitlements(owned.toSet(), pending.toSet())
    }
}

/**
 * Ads on the desktop and in tests, in place of Google's: consent answers as [consent] says, with privacy choices when
 * [privacy] isn't NONE; the native card is a labelled stand-in drawn as Android's NativeAdSlot lays out its card; the
 * full-screen ad loads when [fills] and is counted when shown, until [close]. [steps] records the order of consent,
 * tracking and the SDK's start.
 */
class FakeAds(var consent: Boolean = true, var privacy: AdPrivacy = AdPrivacy.NONE, var fills: Boolean = true) : Ads {
    val steps = mutableListOf<String>()
    var loads = 0
        private set
    var shows = 0
        private set
    var drops = 0
        private set
    var privacyShown = 0
        private set
    private var loaded = false
    private var closing: (() -> Unit)? = null

    /** A full-screen ad is up. */
    var showing by mutableStateOf(false)
        private set

    override fun askConsent(done: (canRequestAds: Boolean, privacyOptions: Boolean) -> Unit) {
        steps += "consent"
        done(consent, privacy != AdPrivacy.NONE)
    }

    override fun askTracking(done: () -> Unit) {
        steps += "tracking"
        done()
    }

    override fun start(done: () -> Unit) {
        steps += "start"
        done()
    }

    @Composable
    override fun NativeCard(look: AdLook, modifier: Modifier) = AdCardStandIn(look, modifier)

    override fun loadFullScreen(done: (Boolean) -> Unit) {
        loads++
        loaded = fills
        done(fills)
    }

    override fun showFullScreen(done: () -> Unit): Boolean {
        if (!loaded) return false
        loaded = false
        shows++
        showing = true
        closing = done
        return true
    }

    /** The athlete closes the full-screen ad. */
    fun close() {
        showing = false
        closing?.invoke()
        closing = null
    }

    override fun dropFullScreen() {
        if (loaded) drops++
        loaded = false
    }

    override fun showPrivacyOptions(done: (AdPrivacy) -> Unit) {
        privacyShown++
        done(privacy)
    }
}

/**
 * The native card's stand-in, laid out as Android's NativeAdSlot.render lays out Google's card (24 dp corners with a
 * 1 dp outline, the "Ad" label with the advertiser, a 40 dp icon beside the 18 sp headline, the body, 160 dp of media,
 * the button) and labelled as a stand-in; on iPhone Google's own ad is drawn there in the same layout.
 */
@Composable
fun AdCardStandIn(look: AdLook, modifier: Modifier = Modifier) {
    val type = Street.type
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(look.surface)
            .border(1.dp, look.outline, RoundedCornerShape(24.dp))
            .semantics { contentDescription = AD_STAND_IN }
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(end = 32.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.defaultMinSize(24.dp, 20.dp).border(1.dp, look.outline, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Ad", style = TextStyle(color = look.text, fontSize = 12.sp, fontFamily = type.bold))
                }
                BasicText("Test advertiser", Modifier.padding(start = 8.dp),
                    style = TextStyle(color = look.muted, fontSize = 12.sp, fontFamily = type.body))
            }
            // Google's AdChoices control sits in this corner.
            BasicText("ⓘ", Modifier.align(Alignment.TopEnd), style = TextStyle(color = look.muted, fontSize = 14.sp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(look.outline))
            BasicText("Native ad stand-in", Modifier.padding(start = 10.dp),
                style = TextStyle(color = look.text, fontSize = 18.sp, fontFamily = type.bold))
        }
        BasicText("Google’s native ad shows here on iPhone, in the theme’s colours.", Modifier.padding(top = 8.dp),
            style = TextStyle(color = look.muted, fontSize = 14.sp, fontFamily = type.body))
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(160.dp).background(look.outline), contentAlignment = Alignment.Center) {
            BasicText("Media", style = TextStyle(color = look.muted, fontSize = 14.sp, fontFamily = type.body))
        }
        Box(
            Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(look.accent)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Open", style = TextStyle(color = look.onAccent, fontSize = 14.sp, fontFamily = type.body, textAlign = TextAlign.Center))
        }
    }
}

/** What VoiceOver hears on the stand-in card, and what tests find it by. */
const val AD_STAND_IN = "Ad stand-in"
