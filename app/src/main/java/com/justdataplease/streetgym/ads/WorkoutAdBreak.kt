package com.justdataplease.streetgym.ads

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.ump.UserMessagingPlatform
import com.justdataplease.monetization.AdPalette
import com.justdataplease.monetization.NativeAdSlot
import com.justdataplease.streetgym.BuildConfig
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.timer.TimingStore
import com.justdataplease.streetgym.ui.*

/** Full-screen native placement: our separate Close control is available immediately. */
class WorkoutAdBreak(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences("streetgym_ad_breaks", Context.MODE_PRIVATE)
    private val clock = AdBreakClock(prefs.getLong("foreground_ms", 0).coerceAtLeast(0))
    private var ready: NativeAd? = null
    private var loadedAt = 0L
    private var attemptedAt = -60_000L
    private var loading = false
    private var resumed = false
    private var destroyed = false
    private var dialog: Dialog? = null

    fun resume() { resumed = true; clock.resume(SystemClock.elapsedRealtime()) }
    fun pause() {
        resumed = false
        prefs.edit().putLong("foreground_ms", clock.pause(SystemClock.elapsedRealtime())).apply()
        dialog?.dismiss()
    }
    fun destroy() { destroyed = true; pause(); ready?.destroy(); ready = null }

    fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (Entitlement.adFree(activity)) { ready?.destroy(); ready = null; dialog?.dismiss(); clock.shown(now); return }
        if (!UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) { ready?.destroy(); ready = null; return }
        if (ready != null && now - loadedAt > 50 * 60_000L) { ready?.destroy(); ready = null }
        if (!resumed || destroyed || ready != null || loading || dialog != null ||
            clock.elapsed(now) < clock.interval - 60_000L || now - attemptedAt < 60_000L) return
        attemptedAt = now; loading = true
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread {
                if (Entitlement.adFree(activity) || destroyed || !resumed || !UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) {
                    loading = false; return@runOnUiThread
                }
                AdLoader.Builder(activity, BuildConfig.ADMOB_FULLSCREEN_NATIVE_ID)
                    .forNativeAd { ad ->
                        loading = false
                        if (Entitlement.adFree(activity) || destroyed || !resumed || !UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) ad.destroy()
                        else { ready?.destroy(); ready = ad; loadedAt = SystemClock.elapsedRealtime() }
                    }
                    .withNativeAdOptions(NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build()).build())
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) { loading = false }
                    }).build().loadAd(AdRequest.Builder().build())
            }
        }
    }

    /** Called only by section completion or a deliberate navigation away from a workout. */
    fun atNaturalBreak() {
        val now = SystemClock.elapsedRealtime()
        val timing = TimingStore(activity).load()
        if (Entitlement.adFree(activity) || !resumed || destroyed || dialog != null || !clock.due(now) || activity.isFinishing ||
            timing.countdown.running || timing.stopwatch.running || timing.timerRinging || timing.alarmRinging ||
            !UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) return
        val ad = ready ?: return
        ready = null
        val modal = Dialog(activity)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.BACKGROUND)
            setPadding(activity.dp(20), activity.dp(16), activity.dp(20), activity.dp(16))
        }
        // This header is outside NativeAdView and never overlays AdChoices or advertiser assets.
        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(activity.bodyText("Ad", 14f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(activity.secondaryButton("Close ×") { modal.dismiss() }, LinearLayout.LayoutParams(activity.dp(108), activity.dp(52)))
        }
        layout.addView(header)
        val slot = NativeAdSlot(activity, BuildConfig.ADMOB_FULLSCREEN_NATIVE_ID,
            AdPalette(AppColors.SURFACE, AppColors.TEXT, AppColors.TEXT_SECONDARY, AppColors.ACTION, AppColors.ON_ACCENT, AppColors.DIVIDER))
        slot.bind(ad)
        val scroll = ScrollView(activity).apply { isFillViewport = true
            addView(LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, activity.dp(16), 0, activity.dp(16)); addView(slot)
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        layout.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        modal.setContentView(layout)
        modal.setCancelable(true)
        modal.setOnDismissListener { slot.clear(); dialog = null }
        dialog = modal
        modal.show()
        modal.window?.setBackgroundDrawableResource(android.R.color.transparent)
        modal.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        clock.shown(now)
        prefs.edit().putLong("foreground_ms", 0).apply()
    }
}
