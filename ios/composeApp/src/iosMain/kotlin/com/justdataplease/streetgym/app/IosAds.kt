package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.justdataplease.streetgym.app.data.AdLook
import com.justdataplease.streetgym.app.data.AdPrivacy
import com.justdataplease.streetgym.app.data.Ads
import platform.UIKit.UIView

/**
 * What Swift does with Google's SDKs (iosApp/StreetGym/GoogleAds.swift): the User Messaging Platform's consent, Apple's
 * tracking question, the Google Mobile Ads SDK's native cards and full-screen ad. Kotlin can't read those Swift
 * frameworks on Windows, so Swift makes them and Kotlin decides when (common Kotlin's AdConsent and AdBreaks). Swift
 * sees this as the protocol `AdsBridge`, and answers on the main thread through [AdsBridgeListener].
 */
interface AdsBridge {
    fun attach(listener: AdsBridgeListener)

    /** UMP: the consent information brought up to date, then its form where the region needs it. */
    fun askConsent()

    /** Apple's App Tracking Transparency question, when it wasn't answered yet. */
    fun askTracking()

    /** Starts the Google Mobile Ads SDK. */
    fun startSdk()

    /** A native card: a view holding Google's NativeAdView, which loads one ad when told; [listener] hears its height. */
    fun makeCard(listener: AdCardListener): AdCardBridge

    fun loadFullScreen()

    /** Shows the loaded full-screen ad over the app; false when none is loaded. */
    fun showFullScreen(): Boolean

    fun dropFullScreen()

    /** UMP's privacy options form, when Google requires one. */
    fun showPrivacyOptions()
}

/** What Swift's ads tell the app; Swift sees it as the protocol `AdsBridgeListener`. */
interface AdsBridgeListener {
    fun consentEnded(canRequestAds: Boolean, privacyOptions: Boolean)
    fun trackingEnded()
    fun sdkStarted()
    fun fullScreenLoaded(ready: Boolean)
    fun fullScreenClosed()

    /** [result]: 0 the form showed, 1 none is required now, 2 it couldn't show. */
    fun privacyEnded(result: Int)
}

/** A native card made in Swift: its view, its theme colours (ARGB), loading its ad, and letting it go. */
interface AdCardBridge {
    val view: UIView
    fun setLook(surface: Long, text: Long, muted: Long, accent: Long, onAccent: Long, outline: Long)
    fun loadAd()

    /** The card's height in points at [width] points, once its ad is in; 0 before. */
    fun fitHeight(width: Double): Double
    fun destroy()
}

/** What a card tells the app: its ad came ([loaded]), or none did. */
interface AdCardListener {
    fun cardChanged(loaded: Boolean)
}

/** The app's [Ads] over Swift's Google SDKs ([AdsBridge]). */
internal class IosAds(private val bridge: AdsBridge) : Ads, AdsBridgeListener {
    private var consentDone: ((Boolean, Boolean) -> Unit)? = null
    private var trackingDone: (() -> Unit)? = null
    private var startDone: (() -> Unit)? = null
    private var loadDone: ((Boolean) -> Unit)? = null
    private var closeDone: (() -> Unit)? = null
    private var privacyDone: ((AdPrivacy) -> Unit)? = null

    init {
        bridge.attach(this)
    }

    override fun askConsent(done: (canRequestAds: Boolean, privacyOptions: Boolean) -> Unit) {
        consentDone = done
        bridge.askConsent()
    }

    override fun askTracking(done: () -> Unit) {
        trackingDone = done
        bridge.askTracking()
    }

    override fun start(done: () -> Unit) {
        startDone = done
        bridge.startSdk()
    }

    /**
     * The card, shown by Compose through UIKitView once its ad is in, at the height Swift measures for the page's width;
     * until then it takes no room (Android: NativeAdSlot is wrap-content). The page keeps its scroll; the card takes taps.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun NativeCard(look: AdLook, modifier: Modifier) {
        var shown by remember { mutableStateOf(false) }
        val card = remember {
            bridge.makeCard(object : AdCardListener {
                override fun cardChanged(loaded: Boolean) { shown = loaded }
            })
        }
        DisposableEffect(card) {
            card.loadAd()
            onDispose { card.destroy() }
        }
        LaunchedEffect(card, look) {
            card.setLook(argb(look.surface), argb(look.text), argb(look.muted), argb(look.accent), argb(look.onAccent), argb(look.outline))
        }
        if (!shown) return
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val height = card.fitHeight(maxWidth.value.toDouble())
            if (height > 0.0) UIKitView(
                factory = { card.view },
                modifier = Modifier.fillMaxWidth().height(height.dp),
                properties = UIKitInteropProperties(
                    interactionMode = UIKitInteropInteractionMode.Cooperative(),
                    isNativeAccessibilityEnabled = true,
                ),
            )
        }
    }

    override fun loadFullScreen(done: (Boolean) -> Unit) {
        loadDone = done
        bridge.loadFullScreen()
    }

    override fun showFullScreen(done: () -> Unit): Boolean {
        closeDone = done
        val shown = bridge.showFullScreen()
        if (!shown) closeDone = null
        return shown
    }

    override fun dropFullScreen() = bridge.dropFullScreen()

    override fun showPrivacyOptions(done: (AdPrivacy) -> Unit) {
        privacyDone = done
        bridge.showPrivacyOptions()
    }

    override fun consentEnded(canRequestAds: Boolean, privacyOptions: Boolean) {
        consentDone?.also { consentDone = null }?.invoke(canRequestAds, privacyOptions)
    }

    override fun trackingEnded() {
        trackingDone?.also { trackingDone = null }?.invoke()
    }

    override fun sdkStarted() {
        startDone?.also { startDone = null }?.invoke()
    }

    override fun fullScreenLoaded(ready: Boolean) {
        loadDone?.also { loadDone = null }?.invoke(ready)
    }

    override fun fullScreenClosed() {
        closeDone?.also { closeDone = null }?.invoke()
    }

    override fun privacyEnded(result: Int) {
        val answer = when (result) {
            0 -> AdPrivacy.SHOWN
            1 -> AdPrivacy.NONE
            else -> AdPrivacy.UNAVAILABLE
        }
        privacyDone?.also { privacyDone = null }?.invoke(answer)
    }

    private fun argb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
}
