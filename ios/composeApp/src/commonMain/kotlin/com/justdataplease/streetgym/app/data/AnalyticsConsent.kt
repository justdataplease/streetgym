package com.justdataplease.streetgym.app.data

/** UMP's consent status (Android: ConsentInformation.ConsentStatus; iPhone: UMPConsentStatus), without the phone's SDK. */
enum class UmpStatus { UNKNOWN, NOT_REQUIRED, REQUIRED, OBTAINED }

/**
 * What Google's consent SDK knows now (Android: ConsentWatcher.update's reads): its [status], and the two keys it keeps
 * in the phone's defaults, [consentModeValues] (`UMP_consentModeValues`) and [gdprApplies] (`IABTCF_gdprApplies`, as
 * [AnalyticsConsent.gdprApplies] reads it). On iPhone the status comes from Swift and the keys from
 * NSUserDefaults.standardUserDefaults (iosMain's IosAds).
 */
data class UmpState(val status: UmpStatus, val consentModeValues: String?, val gdprApplies: Int?) {
    companion object {
        /** Nothing known: Google Analytics' consent stays as it was. */
        val UNKNOWN = UmpState(UmpStatus.UNKNOWN, null, null)
    }
}

/**
 * Google Analytics follows Google's consent form for the ads (android-streetgym 2.15.0, analytics/AnalyticsConsent.kt,
 * line for line; the owner, 26 Nov 2026: "follow AdMob's form"): in the EEA, the UK and Switzerland the athlete's choice
 * there decides; elsewhere everything is granted. No screen of its own.
 *
 * How, from Google's docs (Android's sources, read 26 Nov 2026, and the iPhone's, read 27 Nov 2026):
 * - https://developers.google.com/admob/ios/privacy/consent-mode: UMP 3.0.0 or higher interprets the form's choices for
 *   Consent Mode once consent mode is on in AdMob, and Firebase starts before UMP ("Initialize Firebase before starting
 *   the UMP SDK": iosApp/StreetGym's FirebaseSetup is configured when the app's view controller is made, before any
 *   consent call). "The UMP SDK updates the consent state when European Union (EU) regulations apply to the user"; the
 *   app sets the state when they don't.
 * - https://support.google.com/admob/answer/16053245: AdMob › Privacy & messaging › European regulations › settings,
 *   "Enable consent mode for advertising purposes" and "Enable consent mode for analytics purposes" (the owner's step,
 *   for the iPhone app's message too).
 * - https://developers.google.com/funding-choices/fc-sdks#consent_mode_basic_mode_support: basic mode (collection off
 *   until the choice) and the `UMP_consentModeValues` key UMP stores (on iPhone in NSUserDefaults.standardUserDefaults,
 *   read with stringForKey): one digit each for ad_storage, ad_user_data, ad_personalization and analytics_storage; 0 no
 *   value yet, 1 granted, 2 denied, 3 consent mode does not apply to this user, 4 not configured for this purpose.
 * - https://developers.google.com/tag-platform/security/guides/app-consent?platform=ios: the Info.plist's defaults
 *   (iosApp/project.yml), and setConsent, which overrides them and persists across launches.
 * - https://developers.google.com/tag-platform/security/guides/implement-TCF-strings: Google's own TCF mapping, which
 *   Firebase Analytics applies by itself from the IABTCF_ keys: Purpose 1 denied → ad_storage and ad_user_data denied,
 *   3 or 4 → ad_personalization, 7 → ad_user_data. It maps no purpose to analytics_storage, so the app reads no TCF
 *   purpose for it: without UMP's analytics digit, Europe stays denied.
 */
object AnalyticsConsent {
    /** UMP's consent mode digits (in the phone's defaults, beside the IABTCF_ keys). */
    const val CONSENT_MODE_VALUES = "UMP_consentModeValues"
    /** TCF v2: 1 when GDPR applies to this user, 0 when it doesn't. */
    const val GDPR_APPLIES = "IABTCF_gdprApplies"

    private fun ConsentKind.digit(): Int = when (this) {
        ConsentKind.AD_STORAGE -> 0
        ConsentKind.AD_USER_DATA -> 1
        ConsentKind.AD_PERSONALIZATION -> 2
        ConsentKind.ANALYTICS_STORAGE -> 3
    }

    /**
     * Each consent type, granted or denied; null while UMP hasn't said anything yet (the state stays as it was: denied on
     * a first launch). A choice made in the form always counts (1 granted, 2 denied). Otherwise: the form still to be
     * answered denies everything; Europe denies what the form didn't decide; outside Europe everything is granted.
     */
    fun decide(status: UmpStatus, consentModeValues: String?, gdprApplies: Int?): Map<ConsentKind, Boolean>? {
        if (status == UmpStatus.UNKNOWN) return null
        // A first launch in Europe, or a choice that must be asked again: nothing until the form is answered.
        if (status == UmpStatus.REQUIRED) return ConsentKind.entries.associateWith { false }
        // Europe: the TCF data says GDPR applies, or the form was answered and nothing says GDPR doesn't apply.
        val regulated = gdprApplies == 1 || (status == UmpStatus.OBTAINED && gdprApplies != 0)
        return ConsentKind.entries.associateWith { kind ->
            when (consentModeValues?.getOrNull(kind.digit())) {
                '1' -> true
                '2' -> false
                // Consent mode doesn't apply to this user: outside Europe, unless the TCF data says otherwise.
                '3' -> gdprApplies != 1
                // No value yet (0), consent mode not switched on in AdMob (4), or no digits at all.
                else -> !regulated
            }
        }
    }

    /**
     * UMP's status from Android's numbering (ConsentInformation.ConsentStatus: 0 unknown, 1 not required, 2 required,
     * 3 obtained), which Swift's bridge also answers in (GoogleAds.swift consentStatus, from UMPConsentStatus's cases).
     */
    fun status(value: Int): UmpStatus = when (value) {
        NOT_REQUIRED -> UmpStatus.NOT_REQUIRED
        REQUIRED -> UmpStatus.REQUIRED
        OBTAINED -> UmpStatus.OBTAINED
        else -> UmpStatus.UNKNOWN
    }

    const val NOT_REQUIRED = 1
    const val REQUIRED = 2
    const val OBTAINED = 3

    /**
     * IABTCF_gdprApplies as stored (an integer by the TCF spec; a string or long read too), null when missing. On iPhone
     * NSUserDefaults' stringForKey gives a stored number as its digits.
     */
    fun gdprApplies(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }
}

/**
 * Keeps Google Analytics' consent in step with Google's consent form ([AnalyticsConsent]; Android: ConsentWatcher). It
 * asks UMP for the consent state once per launch, for every tier (Plus and Pro have no ads, so no ad asks for them),
 * and decides again whenever the form or Settings › About › Ad privacy is answered ([AdConsent] tells it, where Android
 * hears UMP write its IABTCF_ and UMP_ keys) and whenever the app comes back ([refresh] from AppModel.opened). It never
 * shows the form: the ads do, on Free, as before. Without Firebase ([GoogleAnalytics.available]) it does nothing, not even
 * ask UMP.
 */
class ConsentWatcher(private val ads: Ads, private val analytics: GoogleAnalytics) {
    private var requested = false

    /** Decides from what UMP knows now. */
    fun update() {
        if (!analytics.available) return
        val state = ads.consentState()
        analytics.consentUpdated(AnalyticsConsent.decide(state.status, state.consentModeValues, state.gdprApplies))
    }

    /** The app opened or came back: decides from the saved state, then asks UMP for today's (once per launch). */
    fun refresh() {
        update()
        if (requested || !analytics.available) return
        requested = true
        ads.updateConsentInfo { update() }
    }
}
