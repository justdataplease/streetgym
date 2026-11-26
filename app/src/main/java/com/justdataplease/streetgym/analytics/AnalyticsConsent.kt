package com.justdataplease.streetgym.analytics

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** UMP's consent status (ConsentInformation.ConsentStatus), without Android. */
enum class UmpStatus { UNKNOWN, NOT_REQUIRED, REQUIRED, OBTAINED }

/**
 * Google Analytics follows Google's consent form for the ads (the owner, 26 Nov 2026: "follow AdMob's form"): in the EEA,
 * the UK and Switzerland the athlete's choice there decides; elsewhere everything is granted. No screen of its own.
 *
 * How, from Google's docs (read 26 Nov 2026):
 * - https://developers.google.com/admob/android/privacy/consent-mode: UMP 3.2.0+ interprets the form's choices for
 *   Consent Mode (ad storage, ad personalization, ad user data and analytics storage) once consent mode is on in AdMob
 *   and Firebase starts before UMP. "The UMP SDK updates the consent state when European Union (EU) regulations apply to
 *   the user"; the app sets the state when they don't.
 * - https://support.google.com/admob/answer/16053245: AdMob › Privacy & messaging › European regulations › settings,
 *   "Enable consent mode for advertising purposes" and "Enable consent mode for analytics purposes" (the owner's step).
 * - https://developers.google.com/funding-choices/fc-sdks#consent_mode_basic_mode_support: basic mode (collection off
 *   until the choice) and the `UMP_consentModeValues` key UMP stores: one digit each for ad_storage, ad_user_data,
 *   ad_personalization and analytics_storage; 0 no value yet, 1 granted, 2 denied, 3 consent mode does not apply to
 *   this user, 4 not configured for this purpose.
 * - https://developers.google.com/tag-platform/security/guides/app-consent?platform=android: the manifest's defaults, and
 *   setConsent, which overrides them and persists across launches.
 * - https://developers.google.com/tag-platform/security/guides/implement-TCF-strings: Google's own TCF mapping, which
 *   Firebase Analytics (21.6.1+) applies by itself from the IABTCF_ keys: Purpose 1 denied → ad_storage and ad_user_data
 *   denied, 3 or 4 → ad_personalization, 7 → ad_user_data. It maps no purpose to analytics_storage, so the app reads no
 *   TCF purpose for it: without UMP's analytics digit, Europe stays denied.
 */
object AnalyticsConsent {
    /** UMP's consent mode digits (in the default SharedPreferences, beside the IABTCF_ keys). */
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

    fun status(value: Int): UmpStatus = when (value) {
        ConsentInformation.ConsentStatus.NOT_REQUIRED -> UmpStatus.NOT_REQUIRED
        ConsentInformation.ConsentStatus.REQUIRED -> UmpStatus.REQUIRED
        ConsentInformation.ConsentStatus.OBTAINED -> UmpStatus.OBTAINED
        else -> UmpStatus.UNKNOWN
    }

    /** IABTCF_gdprApplies as stored (an integer by the TCF spec; a string or long read too), null when missing. */
    fun gdprApplies(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }
}

/**
 * Keeps Google Analytics' consent in step with Google's consent form ([AnalyticsConsent]). It asks UMP for the consent
 * state once per launch, for every tier (Plus and Pro have no ads, so no ad asks for them), and decides again whenever
 * the form or Settings › Ad privacy stores a choice (UMP writes the IABTCF_ and UMP_ keys to the default
 * SharedPreferences) and whenever the app comes back. It never shows the form: the ads do, on Free, as before.
 */
class ConsentWatcher private constructor(private val app: Context) {
    private val defaults: SharedPreferences = app.getSharedPreferences(app.packageName + "_preferences", Context.MODE_PRIVATE)
    // Held here: SharedPreferences keeps its listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("IABTCF_") || key.startsWith("UMP_")) update()
    }
    private var requested = false

    init { defaults.registerOnSharedPreferenceChangeListener(listener) }

    /** Decides from what UMP knows now. */
    fun update() {
        val info = runCatching { UserMessagingPlatform.getConsentInformation(app) }.getOrNull() ?: return
        val values = runCatching { defaults.getString(AnalyticsConsent.CONSENT_MODE_VALUES, null) }.getOrNull()
        val applies = AnalyticsConsent.gdprApplies(runCatching { defaults.all[AnalyticsConsent.GDPR_APPLIES] }.getOrNull())
        GoogleAnalytics.get().consentUpdated(AnalyticsConsent.decide(AnalyticsConsent.status(info.consentStatus), values, applies))
    }

    /** The app opened: decides from the saved state, then asks UMP for today's (once per launch). */
    fun refresh(activity: Activity) {
        update()
        if (requested) return
        requested = true
        runCatching {
            UserMessagingPlatform.getConsentInformation(app).requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(),
                { update() }, { update() })
        }
    }

    companion object {
        @Volatile private var instance: ConsentWatcher? = null

        /** The watcher, or null when the build has no Firebase ([GoogleAnalytics.available]). */
        fun get(context: Context): ConsentWatcher? {
            if (!GoogleAnalytics.get().available) return null
            return instance ?: synchronized(this) { instance ?: ConsentWatcher(context.applicationContext).also { instance = it } }
        }
    }
}
