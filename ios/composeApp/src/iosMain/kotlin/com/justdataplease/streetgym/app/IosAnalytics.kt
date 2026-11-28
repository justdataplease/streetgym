package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.AnalyticsSink
import com.justdataplease.streetgym.app.data.ConsentKind

/*
 * Google Analytics on iPhone (iosApp/StreetGym/GoogleAnalyticsBridge.swift): Swift talks to Firebase Analytics, as
 * Kotlin can't read its Swift API; common Kotlin (data/GoogleAnalytics.kt) decides what is logged and when, as Android's
 * GoogleAnalytics does. Swift sees this as the protocol NativeAnalytics. Without GoogleService-Info.plist Swift says so
 * ([NativeAnalytics.configured] false) and the app has no Google Analytics at all, as Android's builds without Firebase.
 */

/** What Swift's Firebase Analytics does (Android: FirebaseAnalyticsSink's FirebaseAnalytics calls). */
interface NativeAnalytics {
    /** Firebase is configured (the app has GoogleService-Info.plist); asking configures it, before UMP ever starts. */
    fun configured(): Boolean

    /** Analytics.setConsent with each Consent Mode v2 type granted or denied. */
    fun setConsent(adStorage: Boolean, adUserData: Boolean, adPersonalization: Boolean, analyticsStorage: Boolean)

    /** Analytics.setAnalyticsCollectionEnabled. */
    fun setCollectionEnabled(enabled: Boolean)

    /** Analytics.logEvent: [parameters] are text, or whole numbers (Kotlin Long, an NSNumber to Swift), as Android's Bundle. */
    fun logEvent(name: String, parameters: Map<String, Any>)

    /** Analytics.setUserProperty; null clears it. */
    fun setUserProperty(name: String, value: String?)
}

/** The app's [AnalyticsSink] over Swift's [NativeAnalytics] (Android: FirebaseAnalyticsSink). */
internal class IosAnalytics(private val native: NativeAnalytics) : AnalyticsSink {
    override fun setConsent(consent: Map<ConsentKind, Boolean>) = native.setConsent(
        adStorage = consent[ConsentKind.AD_STORAGE] == true,
        adUserData = consent[ConsentKind.AD_USER_DATA] == true,
        adPersonalization = consent[ConsentKind.AD_PERSONALIZATION] == true,
        analyticsStorage = consent[ConsentKind.ANALYTICS_STORAGE] == true,
    )

    override fun setCollectionEnabled(enabled: Boolean) = native.setCollectionEnabled(enabled)

    /** Numbers as Android puts them in its Bundle: whole ones as Long, a Double as it is; anything else as text. */
    override fun log(event: String, params: Map<String, Any>) = native.logEvent(event, params.mapValues { (_, value) ->
        when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value
            else -> value.toString()
        }
    })

    override fun userProperty(name: String, value: String?) = native.setUserProperty(name, value)
}
