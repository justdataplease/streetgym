package com.justdataplease.streetgym.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.UserMessagingPlatform
import com.justdataplease.streetgym.BuildConfig
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.timer.TimingStore

/**
 * The full-screen ad: an AdMob interstitial on its own unit ([BuildConfig.ADMOB_INTERSTITIAL_ID]), Free only, at a
 * natural break and at most once per [AdBreakClock.GAP_MS] of foreground use ([AdBreakPolicy]).
 */
class WorkoutAdBreak(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences("streetgym_ad_breaks", Context.MODE_PRIVATE)
    private val clock = AdBreakClock(prefs.getLong("foreground_ms", 0).coerceAtLeast(0))
    private var ready: InterstitialAd? = null
    private var loadedAt = 0L
    private var attemptedAt = -60_000L
    private var loading = false
    private var resumed = false
    private var destroyed = false
    private var showing = false

    private fun consent() = UserMessagingPlatform.getConsentInformation(activity).canRequestAds()

    fun resume() { resumed = true; clock.resume(SystemClock.elapsedRealtime()) }
    fun pause() {
        resumed = false
        prefs.edit().putLong("foreground_ms", clock.pause(SystemClock.elapsedRealtime())).apply()
    }
    fun destroy() { destroyed = true; pause(); ready = null }

    /** Every second: keeps an ad ready from a minute before the gap ends; Plus and Pro hold none. */
    fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (Entitlement.adFree(activity)) { ready = null; clock.shown(now); return }
        if (!consent()) { ready = null; return }
        // Google's ads expire after an hour; fetch a new one a little before.
        if (ready != null && now - loadedAt > 50 * 60_000L) ready = null
        if (!resumed || destroyed || ready != null || loading || showing || now - attemptedAt < 60_000L ||
            !AdBreakPolicy.mayLoad(adFree = false, consent = true, elapsed = clock.elapsed(now), interval = clock.interval)) return
        attemptedAt = now; loading = true
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread {
                if (Entitlement.adFree(activity) || destroyed || !resumed || !consent()) { loading = false; return@runOnUiThread }
                InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            loading = false
                            if (!Entitlement.adFree(activity) && !destroyed && consent()) {
                                ready = ad; loadedAt = SystemClock.elapsedRealtime()
                            }
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) { loading = false }
                    })
            }
        }
    }

    /**
     * Called only by a finished section or a deliberate navigation away from a workout, never mid-set or during a rest.
     * [covered]: a paywall is in view, as a dialog or on the screen.
     */
    fun atNaturalBreak(covered: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val timing = TimingStore(activity).load()
        val timingBusy = timing.countdown.running || timing.stopwatch.running || timing.timerRinging || timing.alarmRinging
        if (destroyed || showing || activity.isFinishing || !AdBreakPolicy.mayShow(adFree = Entitlement.adFree(activity),
                due = clock.due(now), resumed = resumed, timing = timingBusy, covered = covered, consent = consent())) return
        val ad = ready ?: return
        ready = null
        showing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { showing = false }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { showing = false }
        }
        clock.shown(now)
        prefs.edit().putLong("foreground_ms", 0).apply()
        ad.show(activity)
    }
}
