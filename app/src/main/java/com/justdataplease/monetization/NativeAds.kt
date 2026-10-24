package com.justdataplease.monetization

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.WeakHashMap

data class AdPalette(val surface: Int, val text: Int, val muted: Int, val accent: Int, val onAccent: Int, val outline: Int)

/** One lifecycle owner per host Activity. No document, drawing or workout data is sent. */
object NativeAds {
    private val owners = WeakHashMap<Activity, NativeAdOwner>()

    internal fun owner(activity: Activity, unitId: String): NativeAdOwner =
        owners.getOrPut(activity) { NativeAdOwner(activity, unitId) }

    internal fun forget(activity: Activity) { owners.remove(activity) }

    fun showPrivacy(context: Context, unitId: String) {
        context.activity()?.let { owner(it, unitId).showPrivacy() }
    }
}

internal fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.activity()
    else -> null
}

internal class NativeAdOwner(private val activity: Activity, private val unitId: String) : Application.ActivityLifecycleCallbacks {
    private val handler = Handler(Looper.getMainLooper())
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private val slots = mutableSetOf<NativeAdSlot>()
    private val gate = AdRequestGate()
    private var destroyed = false
    // An owner may be created from a menu/dialog in an already resumed Activity.
    // Window focus would incorrectly block that privacy entry point indefinitely.
    private var resumed = true
    private var updating = false
    private var updated = false
    private var formHandled = false
    private var formShowing = false
    private var formLoading = false
    private var pendingForm: ConsentForm? = null
    private var privacyPending = false
    private var sdkStarting = false
    private var sdkReady = false

    init { activity.application.registerActivityLifecycleCallbacks(this) }

    fun attach(slot: NativeAdSlot) {
        slots += slot
        updateConsent()
        advance()
    }

    fun detach(slot: NativeAdSlot) {
        slots -= slot
        gate.invalidate()
    }

    private fun alive() = !destroyed && !activity.isFinishing && !activity.isDestroyed

    private fun updateConsent() {
        if (!alive() || updating || updated) return
        updating = true
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            if (alive()) { updating = false; updated = true; advance() }
        }, { error ->
            if (alive()) {
                updating = false
                updated = true
                formHandled = true
                Log.d("NativeAds", "Consent update unavailable (${error.errorCode}); respecting cached consent")
                advance()
            }
        })
    }

    private fun advance() {
        if (!alive() || !resumed || !updated || formShowing) return
        if (privacyPending) { openPrivacy(); return }
        if (slots.isEmpty()) return
        if (!formHandled) {
            if (consent.consentStatus != ConsentInformation.ConsentStatus.REQUIRED) {
                formHandled = true
                advance()
                return
            }
            val form = pendingForm
            if (form != null) {
                pendingForm = null
                formShowing = true
                form.show(activity) { error ->
                    if (alive()) {
                        formShowing = false
                        formHandled = true
                        if (error != null) Log.d("NativeAds", "Consent form unavailable (${error.errorCode})")
                        advance()
                    }
                }
            } else if (!formLoading) {
                formLoading = true
                // Recheck the screen after loading; a slow form must not interrupt a
                // camera, drawing or workout started while the library was waiting.
                UserMessagingPlatform.loadConsentForm(activity, { loaded ->
                    if (alive()) { formLoading = false; pendingForm = loaded; advance() }
                }, { error ->
                    if (alive()) {
                        formLoading = false
                        formHandled = true
                        Log.d("NativeAds", "Consent form unavailable (${error.errorCode})")
                        advance()
                    }
                })
            }
            return
        }
        if (!consent.canRequestAds()) { clearAds(); return }
        if (!sdkReady) {
            if (!sdkStarting) {
                sdkStarting = true
                // SDK may collect signals during initialization: consent is checked above.
                MobileAds.initialize(activity.applicationContext) {
                    handler.post { if (alive()) { sdkReady = true; advance() } }
                }
            }
            return
        }
        slots.toList().forEach { it.loadWhenReady() }
    }

    fun load(slot: NativeAdSlot) {
        if (!alive() || !resumed || !sdkReady || formShowing || !formHandled || slot !in slots) return
        if (!consent.canRequestAds()) { clearAds(); return }
        val now = SystemClock.elapsedRealtime()
        val wait = gate.retryAfter(now)
        if (wait > 0) { slot.retryAfter(wait); return }
        val token = gate.begin(now, consent.canRequestAds(), slot.isAttachedToWindow) ?: return
        val options = NativeAdOptions.Builder()
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
            .build()
        AdLoader.Builder(activity, unitId)
            .forNativeAd { ad ->
                val accepted = gate.finish(token)
                if (accepted && alive() && resumed && !formShowing && consent.canRequestAds() && slot in slots && slot.isAttachedToWindow) {
                    slot.bind(ad)
                } else ad.destroy()
            }
            .withNativeAdOptions(options)
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (gate.finish(token)) Log.d("NativeAds", "Native card unavailable (${error.code})")
                    // No automatic failure loop; retry when the library is next opened.
                }
            }).build().loadAd(AdRequest.Builder().build())
    }

    fun showPrivacy() {
        privacyPending = true
        updateConsent()
        advance()
    }

    private fun openPrivacy() {
        privacyPending = false
        if (consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            clearAds()
            formShowing = true
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (alive()) {
                    formShowing = false
                    pendingForm = null
                    formHandled = true
                    if (error != null) Toast.makeText(activity, "Ad privacy is unavailable. Try again later.", Toast.LENGTH_SHORT).show()
                    advance()
                }
            }
        } else {
            Toast.makeText(activity, "No ad privacy choices are available right now.", Toast.LENGTH_SHORT).show()
            advance()
        }
    }

    private fun clearAds() { gate.invalidate(); slots.toList().forEach { it.clear() } }

    override fun onActivityResumed(a: Activity) { if (a === activity) { resumed = true; advance() } }
    override fun onActivityPaused(a: Activity) { if (a === activity) { resumed = false; gate.invalidate() } }
    override fun onActivityDestroyed(a: Activity) {
        if (a !== activity) return
        destroyed = true
        pendingForm = null
        clearAds()
        slots.clear()
        handler.removeCallbacksAndMessages(null)
        activity.application.unregisterActivityLifecycleCallbacks(this)
        NativeAds.forget(activity)
    }
    override fun onActivityCreated(a: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(a: Activity) = Unit
    override fun onActivityStopped(a: Activity) = Unit
    override fun onActivitySaveInstanceState(a: Activity, state: Bundle) = Unit
}

/** Wrap-content: occupies no space until an ad loads; always below the primary task. */
class NativeAdSlot(context: Context, private val unitId: String, private var palette: AdPalette) : FrameLayout(context) {
    private var owner: NativeAdOwner? = null
    private var ad: NativeAd? = null
    private var adView: NativeAdView? = null
    private var loadedAt = 0L
    private val retry = Runnable { loadWhenReady() }

    init { clipChildren = false; clipToPadding = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.activity()?.let { owner = NativeAds.owner(it, unitId); owner?.attach(this) }
    }

    override fun onDetachedFromWindow() {
        owner?.detach(this)
        owner = null
        clear()
        super.onDetachedFromWindow()
    }

    internal fun loadWhenReady() {
        if (ad != null && SystemClock.elapsedRealtime() - loadedAt > 50 * 60_000L) clear()
        if (isAttachedToWindow && ad == null) owner?.load(this)
    }

    internal fun retryAfter(delay: Long) { removeCallbacks(retry); postDelayed(retry, delay) }

    internal fun clear() {
        removeCallbacks(retry)
        adView?.destroy()
        adView = null
        ad?.destroy()
        ad = null
        removeAllViews()
        requestLayout()
    }

    fun updatePalette(next: AdPalette) {
        if (next == palette) return
        palette = next
        ad?.let { render(it) }
    }

    internal fun bind(value: NativeAd) {
        clear()
        ad = value
        loadedAt = SystemClock.elapsedRealtime()
        render(value)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun rounded(color: Int, radius: Int, border: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), palette.outline)
    }
    private fun label(value: CharSequence?, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        // Never ellipsize advertiser text; allow font scaling and long translations.
    }

    private fun render(value: NativeAd) {
        adView?.destroy()
        removeAllViews()
        val native = NativeAdView(context).apply {
            background = rounded(palette.surface, 24, true)
            clipToOutline = false // Leave the SDK's AdChoices overlay unobstructed.
        }
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16)) }
        val attribution = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(32), dp(10)) // Reserved corner for AdChoices.
        }
        attribution.addView(label("Ad", 12f, palette.text, true).apply {
            setPadding(dp(6), dp(2), dp(6), dp(2)); background = rounded(palette.surface, 5, true)
            minWidth = dp(24); minHeight = dp(20)
        })
        val advertiser = label(value.advertiser, 12f, palette.muted)
        attribution.addView(advertiser, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
        advertiser.visibility = if (value.advertiser.isNullOrBlank()) GONE else VISIBLE
        native.advertiserView = advertiser
        body.addView(attribution, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val titleRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageDrawable(value.icon?.drawable) }
        icon.visibility = if (value.icon == null) GONE else VISIBLE
        titleRow.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
        native.iconView = icon
        val headline = label(value.headline, 18f, palette.text, true)
        titleRow.addView(headline, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        native.headlineView = headline
        body.addView(titleRow)
        val description = label(value.body, 14f, palette.muted)
        description.visibility = if (value.body.isNullOrBlank()) GONE else VISIBLE
        body.addView(description, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        native.bodyView = description
        val media = MediaView(context).apply {
            minimumWidth = dp(120); minimumHeight = dp(120)
            setImageScaleType(ImageView.ScaleType.FIT_CENTER)
            mediaContent = value.mediaContent
        }
        body.addView(media, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(160)).apply { topMargin = dp(12) })
        media.visibility = if (value.mediaContent == null) GONE else VISIBLE
        native.mediaView = media
        val action = Button(context).apply {
            text = value.callToAction
            textSize = 14f; isAllCaps = false; minHeight = dp(48)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTextColor(palette.onAccent); background = rounded(palette.accent, 16)
            visibility = if (value.callToAction.isNullOrBlank()) GONE else VISIBLE
        }
        body.addView(action, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        native.callToActionView = action
        native.addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        // Only SDK-registered assets handle ad clicks; the surrounding whitespace isn't clickable.
        native.setNativeAd(value)
        adView = native
        addView(native, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
}
