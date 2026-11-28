package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The colours a native ad card is drawn in, from the theme (Android: monetization/NativeAds.kt AdPalette with
 * AppColors.SURFACE, TEXT, TEXT_SECONDARY, ACTION, ON_ACCENT and DIVIDER).
 */
data class AdLook(val surface: Color, val text: Color, val muted: Color, val accent: Color, val onAccent: Color, val outline: Color)

/**
 * What the phone's ad SDK does: Google's consent (UMP), Apple's tracking question, the Google Mobile Ads SDK's native
 * card and full-screen ad, and Google's privacy choices. On iPhone it is Swift (iosApp/StreetGym/GoogleAds.swift, through
 * iosMain's IosAds); on the desktop a fake that draws a labelled stand-in card. When each may run is decided here in
 * common Kotlin: [AdConsent] and [AdBreaks].
 */
interface Ads {
    /**
     * Google's consent: the consent information brought up to date, and its form shown when the region needs it (Android:
     * NativeAdOwner.updateConsent and advance). [done] hears whether ads may be requested, and whether Google offers
     * privacy choices to change it later.
     */
    fun askConsent(done: (canRequestAds: Boolean, privacyOptions: Boolean) -> Unit) = done(false, false)

    /** Apple's App Tracking Transparency question, when it wasn't answered yet; [done] once it is (ads still show without tracking). */
    fun askTracking(done: () -> Unit) = done()

    /** Starts the ad SDK; [done] once it is ready to load ads (Android: MobileAds.initialize). */
    fun start(done: () -> Unit) = done()

    /** One native card in [look]: it loads its ad and draws it, and takes no room until one loaded (Android: NativeAdSlot). */
    @Composable
    fun NativeCard(look: AdLook, modifier: Modifier) {}

    /** Loads a full-screen ad; [done] says whether one is ready (Android: InterstitialAd.load). */
    fun loadFullScreen(done: (Boolean) -> Unit) = done(false)

    /** Shows the full-screen ad loaded; [done] once it is closed. False when none was ready. */
    fun showFullScreen(done: () -> Unit): Boolean = false

    /** Lets a loaded full-screen ad go: Plus or Pro bought, consent withdrawn, or it grew too old. */
    fun dropFullScreen() {}

    /** Google's privacy choices (Settings › About › Ad privacy); [done] hears whether they showed. */
    fun showPrivacyOptions(done: (AdPrivacy) -> Unit) = done(AdPrivacy.NONE)

    /**
     * What Google's consent SDK knows now, for Google Analytics' consent ([ConsentWatcher]; Android: ConsentWatcher.update):
     * UMP's status and the consent mode and TCF keys it stored. Nothing is asked.
     */
    fun consentState(): UmpState = UmpState.UNKNOWN

    /**
     * Google's consent information brought up to date without its form, on every tier, for Google Analytics' consent
     * (Android: ConsentWatcher.refresh's requestConsentInfoUpdate); [done] once it answered, either way.
     */
    fun updateConsentInfo(done: () -> Unit) = done()

    /** No ads at all: previews, and tests that don't look at ads. */
    object None : Ads
}

/** How Settings › About › Ad privacy went (Android: NativeAdOwner.openPrivacy). */
enum class AdPrivacy {
    /** Google's choices showed. */
    SHOWN,
    /** "No ad privacy choices are available right now." */
    NONE,
    /** "Ad privacy is unavailable. Try again later." */
    UNAVAILABLE,
}

/**
 * Consent first (Android: NativeAdOwner.advance): Google's form where the region needs it, then Apple's tracking
 * question, then the SDK starts; nothing is requested before. Asked once while the app runs, by the first page that ends
 * in a card outside a workout; a workout's cards wait for consent instead of raising the form (Android:
 * ManagedNativePlacement's training). [adFree]: Plus and Pro ask nothing. [answered] hears each answer of Google's
 * consent, after its form or the privacy choices: Google Analytics' consent follows it (Android: ConsentWatcher's
 * listener on UMP's keys).
 */
@Stable
class AdConsent(private val ads: Ads, private val answered: () -> Unit = {}, private val adFree: () -> Boolean) {
    /** Ads may be requested (UMP's canRequestAds). */
    var canRequest by mutableStateOf(false)
        private set

    /** The SDK is ready: cards load. */
    var started by mutableStateOf(false)
        private set

    /** Google offers privacy choices (UMP's privacyOptionsRequirementStatus is required). */
    var privacyOptions by mutableStateOf(false)
        private set

    /** Google's form or Apple's question is up: no full-screen ad over them. */
    var asking by mutableStateOf(false)
        private set

    private var asked = false

    /** Asks, once while the app runs: Google's consent, then (if ads may be requested) Apple's tracking question, then the SDK. */
    fun gather() {
        if (asked || adFree()) return
        asked = true
        asking = true
        ads.askConsent { can, privacy ->
            canRequest = can
            privacyOptions = privacy
            answered()
            if (!can) {
                asking = false
                return@askConsent
            }
            ads.askTracking {
                asking = false
                ads.start { started = true }
            }
        }
    }

    /**
     * Settings › About › Ad privacy: Google's choices, which may change whether ads are requested; the answer is read
     * again afterwards (Android: showPrivacy, openPrivacy).
     */
    fun showPrivacy(done: (AdPrivacy) -> Unit) {
        asking = true
        ads.showPrivacyOptions { result ->
            asking = false
            if (result == AdPrivacy.SHOWN) ads.askConsent { can, privacy ->
                canRequest = can
                privacyOptions = privacy
                answered()
            }
            done(result)
        }
    }
}

/** Only foreground use counts; a missed break never makes a backlog of ads (Android: ads/AdBreakClock.kt, line for line). */
class AdBreakClock(private var banked: Long = 0, val interval: Long = GAP_MS) {
    private var started: Long? = null
    fun resume(now: Long) { if (started == null) started = now }
    fun elapsed(now: Long) = banked + (started?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
    fun pause(now: Long): Long { banked = elapsed(now); started = null; return banked }
    fun due(now: Long) = elapsed(now) >= interval
    fun shown(now: Long) { banked = 0; if (started != null) started = now }

    companion object {
        /** At most one full-screen ad every five minutes of use (the owner, 2026-10-30). */
        const val GAP_MS = 5 * 60_000L
    }
}

/**
 * When the full-screen ad may show (Android: AdBreakPolicy, line for line): Free only, at a change of screen or tab or a
 * finished section, once the gap has passed, never while a rest, timer, stopwatch or alarm runs, never over an offer or
 * the consent form, only with consent, and never within [AFTER_RESUME_MS] of the app opening or coming back.
 */
object AdBreakPolicy {
    const val AFTER_RESUME_MS = 10_000L

    fun mayShow(adFree: Boolean, due: Boolean, resumed: Boolean, timing: Boolean, covered: Boolean, consent: Boolean): Boolean =
        !adFree && due && resumed && !timing && !covered && consent

    /** Loads a minute before the gap ends, so an ad is ready at the next break. */
    fun mayLoad(adFree: Boolean, consent: Boolean, elapsed: Long, interval: Long = AdBreakClock.GAP_MS): Boolean =
        !adFree && consent && elapsed >= interval - 60_000L
}

/**
 * The full-screen ad on Free (Android: ads/WorkoutAdBreak.kt and HomeActivity.adBreakNow): every five minutes of use at
 * most, at the next change of screen or tab or a finished section, never over a screen in use. The foreground time is
 * kept under Android's name ("streetgym_ad_breaks" › foreground_ms), so the gap carries across a restart. Every time
 * comes from [elapsed], a clock that only moves on (tests step it).
 *
 * [adFree]: Plus or Pro. [timingBusy]: a Timer, stopwatch or alarm runs or rings. [resting]: a workout's rest runs.
 */
@Stable
class AdBreaks(
    private val ads: Ads,
    private val consent: AdConsent,
    private val store: KeyValueStore,
    private val elapsed: () -> Long,
    private val adFree: () -> Boolean,
    private val timingBusy: () -> Boolean,
    private val resting: () -> Boolean,
) {
    private val clock = AdBreakClock(store.get(FOREGROUND_MS)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L)
    private var resumed = false
    private var resumedAt = 0L
    private var attemptedAt: Long? = null
    private var loading = false
    private var loadedAt: Long? = null

    /** A full-screen ad is on screen. */
    var showing by mutableStateOf(false)
        private set

    /** How many full-screen ads showed while the app ran (tests). */
    var shown = 0
        private set

    /** The app is on screen (Android: onResume): the clock runs, and the first [AdBreakPolicy.AFTER_RESUME_MS] are the athlete's own. */
    fun resume() {
        resumed = true
        resumedAt = elapsed()
        clock.resume(resumedAt)
    }

    /** The app left the screen (Android: onPause): the clock stops, and its time is kept. */
    fun pause() {
        resumed = false
        store.set(FOREGROUND_MS, clock.pause(elapsed()).toString())
    }

    /**
     * Every second (Android: WorkoutAdBreak.tick): keeps an ad ready from a minute before the gap ends; Plus and Pro hold
     * none, and their time doesn't count toward the next one; an ad loaded 50 minutes ago is let go (Google's expire
     * after an hour).
     */
    fun tick() {
        val now = elapsed()
        if (adFree()) {
            drop()
            clock.shown(now)
            return
        }
        if (!consent.canRequest || !consent.started) {
            drop()
            return
        }
        loadedAt?.let { if (now - it > EXPIRE_MS) drop() }
        if (!resumed || loadedAt != null || loading || showing || attemptedAt?.let { now - it < RETRY_MS } == true ||
            !AdBreakPolicy.mayLoad(adFree = false, consent = true, elapsed = clock.elapsed(now), interval = clock.interval)) return
        attemptedAt = now
        loading = true
        ads.loadFullScreen { ready ->
            loading = false
            if (ready && !adFree() && consent.canRequest) loadedAt = elapsed() else if (ready) ads.dropFullScreen()
        }
    }

    /**
     * A natural break (Android: atNaturalBreak, called by adBreakNow on a change of screen or tab or a finished section):
     * shows the ad when it may. Never during a rest, nor over an offer ([covered]: an offer's dialog, or Settings with
     * its plans), nor while Google's form or Apple's question is up. True when an ad showed.
     */
    fun atBreak(covered: Boolean = false): Boolean {
        if (resting()) return false
        val now = elapsed()
        // Never as the app opens or comes back: the first taps after that are the athlete's own.
        if (now - resumedAt < AdBreakPolicy.AFTER_RESUME_MS) return false
        if (showing || !AdBreakPolicy.mayShow(adFree = adFree(), due = clock.due(now), resumed = resumed, timing = timingBusy(),
                covered = covered || consent.asking, consent = consent.canRequest)) return false
        if (loadedAt == null) return false
        loadedAt = null
        showing = true
        clock.shown(now)
        store.set(FOREGROUND_MS, "0")
        val opened = ads.showFullScreen { showing = false }
        if (!opened) showing = false else shown++
        return opened
    }

    private fun drop() {
        if (loadedAt != null) ads.dropFullScreen()
        loadedAt = null
    }

    companion object {
        /** Android's preferences "streetgym_ad_breaks", "foreground_ms". */
        const val FOREGROUND_MS = "streetgym_ad_breaks.foreground_ms"
        /** A failed load waits a minute before the next try (Android: attemptedAt). */
        const val RETRY_MS = 60_000L
        /** Google's full-screen ads expire after an hour; one is let go a little before. */
        const val EXPIRE_MS = 50 * 60_000L
    }
}

/** Where an ad card goes inside a list on Free (Android: ads/AdRowAdapter.kt and SkillsScreen's adAt). */
object AdRows {
    /** A level's classes: after the first three (Android: AdRowAdapter's after). */
    const val LEVEL_AFTER = 3

    /** Skills: after the fourth ladder (Android: skillsContent's adAt). */
    const val SKILLS_AFTER = 4

    /** The card's place among [count] rows: after the first [after], or at the end of a shorter list. */
    fun position(count: Int, after: Int): Int = minOf(after, count)

    /** In a list of [count] rows with the card after the first [after]: the row at [position], null for the card (Android: adRowBaseIndex). */
    fun baseIndex(position: Int, count: Int, after: Int): Int? {
        val ad = minOf(after, count)
        return when {
            position < ad -> position
            position == ad -> null
            else -> position - 1
        }
    }
}
