package com.justdataplease.streetgym.analytics

import com.justdataplease.streetgym.analytics.ConsentKind.AD_PERSONALIZATION
import com.justdataplease.streetgym.analytics.ConsentKind.AD_STORAGE
import com.justdataplease.streetgym.analytics.ConsentKind.AD_USER_DATA
import com.justdataplease.streetgym.analytics.ConsentKind.ANALYTICS_STORAGE
import com.justdataplease.streetgym.analytics.UmpStatus.NOT_REQUIRED
import com.justdataplease.streetgym.analytics.UmpStatus.OBTAINED
import com.justdataplease.streetgym.analytics.UmpStatus.REQUIRED
import com.justdataplease.streetgym.analytics.UmpStatus.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Google Analytics' consent from Google's consent form (the owner: "follow AdMob's form"). UMP_consentModeValues holds one
 * digit each for ad_storage, ad_user_data, ad_personalization and analytics_storage: 0 no value, 1 granted, 2 denied,
 * 3 doesn't apply to this user, 4 not configured (developers.google.com/funding-choices/fc-sdks).
 */
class AnalyticsConsentTest {
    private val all = ConsentKind.entries
    private fun granted(vararg kinds: ConsentKind) = all.associateWith { it in kinds }

    @Test fun nothingIsDecidedBeforeUmpKnowsTheState() {
        assertNull(AnalyticsConsent.decide(UNKNOWN, null, null))
        assertNull(AnalyticsConsent.decide(UNKNOWN, "1111", 1))
    }

    @Test fun outsideEuropeEverythingIsGranted() {
        assertEquals(granted(*all.toTypedArray()), AnalyticsConsent.decide(NOT_REQUIRED, null, null))
        assertEquals(granted(*all.toTypedArray()), AnalyticsConsent.decide(NOT_REQUIRED, "3333", 0))
        // UMP saying consent mode doesn't apply, with no TCF data.
        assertEquals(granted(*all.toTypedArray()), AnalyticsConsent.decide(NOT_REQUIRED, "3333", null))
    }

    @Test fun aFormStillToBeAnsweredDeniesEverythingEvenWithOlderChoices() {
        assertEquals(granted(), AnalyticsConsent.decide(REQUIRED, null, 1))
        // A choice that must be asked again (expired): the old digits don't count until the form is answered.
        assertEquals(granted(), AnalyticsConsent.decide(REQUIRED, "1111", 1))
    }

    @Test fun inEuropeTheFormsChoiceDecidesEachType() {
        assertEquals(granted(*all.toTypedArray()), AnalyticsConsent.decide(OBTAINED, "1111", 1))
        assertEquals(granted(), AnalyticsConsent.decide(OBTAINED, "2222", 1))
        // Analytics yes, ads personalisation no.
        assertEquals(granted(AD_STORAGE, AD_USER_DATA, ANALYTICS_STORAGE), AnalyticsConsent.decide(OBTAINED, "1121", 1))
        // Ads yes, analytics no.
        assertEquals(granted(AD_STORAGE, AD_USER_DATA, AD_PERSONALIZATION), AnalyticsConsent.decide(OBTAINED, "1112", 1))
    }

    @Test fun inEuropeWhatTheFormDidNotDecideStaysDenied() {
        // Consent mode not switched on in AdMob: no digits, or "not configured".
        assertEquals(granted(), AnalyticsConsent.decide(OBTAINED, null, 1))
        assertEquals(granted(), AnalyticsConsent.decide(OBTAINED, "4444", 1))
        // Advertising purposes on, analytics purposes off: analytics_storage stays denied.
        assertEquals(granted(AD_STORAGE, AD_USER_DATA, AD_PERSONALIZATION), AnalyticsConsent.decide(OBTAINED, "1114", 1))
        // No value yet.
        assertEquals(granted(), AnalyticsConsent.decide(OBTAINED, "0000", 1))
        // An answered form with no TCF data at all counts as Europe.
        assertEquals(granted(), AnalyticsConsent.decide(OBTAINED, null, null))
        // "Doesn't apply" against TCF data that says GDPR applies: denied.
        assertEquals(granted(), AnalyticsConsent.decide(OBTAINED, "3333", 1))
    }

    @Test fun gdprDataOverridesANotRequiredStatus() {
        // Not required although GDPR applies (for example a user tagged under the age of consent): only a yes counts.
        assertEquals(granted(), AnalyticsConsent.decide(NOT_REQUIRED, null, 1))
        assertEquals(granted(ANALYTICS_STORAGE), AnalyticsConsent.decide(NOT_REQUIRED, "2221", 1))
    }

    @Test fun aNoInTheFormCountsOutsideEuropeToo() {
        assertEquals(granted(AD_STORAGE, AD_USER_DATA, AD_PERSONALIZATION), AnalyticsConsent.decide(NOT_REQUIRED, "3332", 0))
        // An answered message that isn't GDPR's (TCF says GDPR doesn't apply): granted unless denied.
        assertEquals(granted(*all.toTypedArray()), AnalyticsConsent.decide(OBTAINED, null, 0))
    }

    @Test fun shortOrOddDigitsFallBackToTheRegion() {
        assertEquals(granted(AD_STORAGE), AnalyticsConsent.decide(OBTAINED, "1", 1))
        assertEquals(granted(AD_STORAGE, AD_USER_DATA, AD_PERSONALIZATION, ANALYTICS_STORAGE), AnalyticsConsent.decide(NOT_REQUIRED, "x", null))
    }

    @Test fun tcfGdprAppliesIsReadAsStored() {
        assertEquals(1, AnalyticsConsent.gdprApplies(1))
        assertEquals(0, AnalyticsConsent.gdprApplies(0L))
        assertEquals(1, AnalyticsConsent.gdprApplies(" 1"))
        assertNull(AnalyticsConsent.gdprApplies(null))
        assertNull(AnalyticsConsent.gdprApplies(true))
    }
}
