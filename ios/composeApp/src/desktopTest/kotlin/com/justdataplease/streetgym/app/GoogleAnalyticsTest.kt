package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.ConsentKind
import com.justdataplease.streetgym.app.data.GoogleAnalytics
import com.justdataplease.streetgym.app.data.KeyValueStore
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.skills.SkillLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Google Analytics runs only with consent and "Share my progress" on, and logs short, fixed values (android-streetgym,
 * GoogleAnalyticsTest, line for line, over the desktop's [FakeAnalyticsSink]), kept under Android's names.
 */
class GoogleAnalyticsTest {
    /** The phone's saved texts, every key in view (Android's test reads MemoryPreferences.all). */
    private class Store : KeyValueStore {
        val all = HashMap<String, String>()
        override fun get(key: String): String? = all[key]
        override fun set(key: String, value: String?) { if (value == null) all.remove(key) else all[key] = value }
    }

    private val sink = FakeAnalyticsSink()
    private val preferences = Store()
    private var sharing = true
    private val allGranted = ConsentKind.entries.associateWith { true }
    private val allDenied = ConsentKind.entries.associateWith { false }

    private fun analytics() = GoogleAnalytics(sink, preferences) { sharing }.also { it.start() }

    private fun program(id: String = "sg:l02:p0005", level: Int = 2, number: Int = 5, dayType: String? = null) = TrainingProgram(
        id, number, level, LocalizedText("Day"), LocalizedText.EMPTY, true, false, null, null, 40, emptyList(), dayType = dayType)

    @Test fun aFirstLaunchCollectsNothingUntilConsentIsGranted() {
        val analytics = analytics()
        assertEquals(listOf(false), sink.collection)
        analytics.screen("today")
        analytics.tutorialBegin()
        analytics.userProperties(Tier.FREE, 1, "concrete")
        assertTrue(sink.events.isEmpty())
        assertTrue(sink.properties.isEmpty())

        analytics.consentUpdated(allGranted)
        assertEquals(listOf(allGranted), sink.consents)
        assertEquals(listOf(false, true), sink.collection)
        // On mid-session: the screen on view and the user properties go up at once.
        assertEquals(listOf(GoogleAnalytics.SCREEN_VIEW to mapOf<String, Any>(GoogleAnalytics.SCREEN_NAME to "today")), sink.events)
        assertEquals(listOf<Pair<String, String?>>("tier" to "free", "level" to "1", "theme" to "concrete"), sink.properties)
    }

    @Test fun deniedAnalyticsStorageKeepsCollectionOffAndTheStateLastsToTheNextLaunch() {
        analytics().consentUpdated(allDenied)
        assertEquals(listOf(false), sink.collection)
        analytics().consentUpdated(allGranted)
        assertEquals(listOf(false, false, true), sink.collection)
        // The next launch starts with what was decided, before UMP answers; kept under Android's name.
        val next = GoogleAnalytics(sink, preferences) { sharing }
        next.start()
        assertTrue(next.collecting)
        assertEquals("true", preferences.all["streetgym_google_analytics.analytics_storage_granted"])
    }

    @Test fun anUnknownConsentStateChangesNothing() {
        val analytics = analytics()
        analytics.consentUpdated(null)
        assertTrue(sink.consents.isEmpty())
        assertFalse(analytics.collecting)
    }

    @Test fun theSameConsentAgainIsNotSentAgain() {
        val analytics = analytics()
        analytics.consentUpdated(allGranted)
        analytics.consentUpdated(allGranted)
        assertEquals(1, sink.consents.size)
    }

    @Test fun shareMyProgressOffStopsCollectionAndOnStartsItAgain() {
        val analytics = analytics()
        analytics.consentUpdated(allGranted)
        analytics.screen("settings")
        sharing = false
        analytics.sharingChanged(false)
        assertEquals(listOf(false, true, false), sink.collection)
        val logged = sink.events.size
        analytics.classStart(program(), Intensity.NORMAL, repeat = false)
        assertEquals(logged, sink.events.size)

        sharing = true
        analytics.sharingChanged(true)
        assertEquals(listOf(false, true, false, true), sink.collection)
        assertEquals(GoogleAnalytics.SCREEN_VIEW to mapOf<String, Any>(GoogleAnalytics.SCREEN_NAME to "settings"), sink.events.last())
    }

    @Test fun shareMyProgressOnWithoutConsentStaysOff() {
        sharing = false
        val analytics = analytics()
        analytics.consentUpdated(allDenied)
        sharing = true
        analytics.sharingChanged(true)
        assertFalse(analytics.collecting)
        assertEquals(listOf(false), sink.collection)
    }

    @Test fun switchedOffBeforeTheLaunchKeepsCollectionOffWhateverTheConsent() {
        sharing = false
        val analytics = analytics()
        analytics.consentUpdated(allGranted)
        assertFalse(analytics.collecting)
        assertEquals(listOf(false), sink.collection)
    }

    @Test fun aRedrawOfTheSameScreenIsNotCountedAgain() {
        val analytics = analytics().apply { consentUpdated(allGranted) }
        analytics.screen("class_overview", "Overview(a)")
        analytics.screen("class_overview", "Overview(a)")
        analytics.screen("class_overview", "Overview(b)")
        analytics.screen("today", "Dashboard")
        analytics.screen("class_overview", "Overview(b)")
        assertEquals(listOf("class_overview", "class_overview", "today", "class_overview"),
            sink.events.map { it.second[GoogleAnalytics.SCREEN_NAME] })
    }

    @Test fun classEventsCarryTheLevelClassAndTheDaysPick() {
        val analytics = analytics().apply { consentUpdated(allGranted) }
        analytics.classStart(program(), Intensity.HARDER, repeat = false)
        analytics.classComplete(program(), Intensity.HARDER, repeat = false, durationSeconds = 37 * 60 + 59)
        assertEquals(GoogleAnalytics.CLASS_START to mapOf<String, Any>("workout_type" to "class", "level" to 2L,
            "class_number" to 5L, "intensity" to "harder", "repeat" to "false"), sink.events[0])
        assertEquals(GoogleAnalytics.CLASS_COMPLETE to mapOf<String, Any>("workout_type" to "class", "level" to 2L,
            "class_number" to 5L, "intensity" to "harder", "repeat" to "false", "duration_min" to 37L), sink.events[1])
    }

    @Test fun ownAndLibraryWorkoutsHaveNoLevelOrClassNumber() {
        val analytics = analytics().apply { consentUpdated(allGranted) }
        analytics.classStart(program(id = "custom:abc", level = 0, number = 0), Intensity.NORMAL, repeat = false)
        analytics.classStart(program(id = "ready:pull-1", level = 0, number = 0), Intensity.NORMAL, repeat = true)
        assertEquals(mapOf<String, Any>("workout_type" to "own", "intensity" to "normal", "repeat" to "false"), sink.events[0].second)
        assertEquals(mapOf<String, Any>("workout_type" to "library", "intensity" to "normal", "repeat" to "true"), sink.events[1].second)
        assertEquals("exam", GoogleAnalytics.workoutType(program(dayType = "exam")))
    }

    @Test fun examsRanksPlacementAndOffers() {
        val analytics = analytics().apply { consentUpdated(allGranted) }
        analytics.examComplete(program(level = 3, number = 100, dayType = "exam"), passed = true)
        analytics.skillRankUp("pullup", SkillLevel.INTERMEDIATE)
        analytics.skillRankUp("pullup", SkillLevel.NONE)
        analytics.tutorialBegin()
        analytics.tutorialBegin()
        analytics.tutorialComplete(2, 1, GoogleAnalytics.METHOD_TEST)
        analytics.paywallView("level", Tier.PRO)
        analytics.paywallTap("settings", GoogleAnalytics.PLAN_PRO_YEARLY)
        analytics.paywallTap("remove_ads", GoogleAnalytics.PLAN_PLUS)
        assertEquals(listOf(
            "exam_complete" to mapOf<String, Any>("level" to 3L, "class_number" to 100L, "passed" to "true"),
            "skill_rank_up" to mapOf<String, Any>("skill_id" to "pullup", "rank" to "intermediate"),
            "tutorial_begin" to emptyMap(),
            "tutorial_complete" to mapOf<String, Any>("start_level" to 2L, "start_class" to 1L, "method" to "test"),
            "paywall_view" to mapOf<String, Any>("source" to "level", "tier" to "pro"),
            "paywall_tap" to mapOf<String, Any>("source" to "settings", "tier" to "pro", "plan" to "pro_yearly"),
            "paywall_tap" to mapOf<String, Any>("source" to "remove_ads", "tier" to "plus", "plan" to "plus"),
        ), sink.events)
        // A new strength test (after a reset) starts again.
        analytics.tutorialBegin()
        assertEquals("tutorial_begin", sink.events.last().first)
    }

    @Test fun userPropertiesGoUpOnlyWhenTheyChange() {
        val analytics = analytics().apply { consentUpdated(allGranted) }
        analytics.userProperties(Tier.FREE, 1, "concrete")
        analytics.userProperties(Tier.FREE, 1, "concrete")
        analytics.userProperties(Tier.PLUS, 4, "concrete")
        analytics.userProperties(Tier.PLUS, null, "concrete")
        assertEquals(listOf<Pair<String, String?>>("tier" to "free", "level" to "1", "theme" to "concrete", "tier" to "plus", "level" to "4", "level" to null),
            sink.properties)
    }

    @Test fun withoutFirebaseEverythingIsANoOp() {
        val analytics = GoogleAnalytics(null, preferences) { true }
        analytics.start()
        analytics.consentUpdated(allGranted)
        analytics.screen("today")
        analytics.sharingChanged(false)
        assertFalse(analytics.available)
        assertFalse(analytics.collecting)
        assertTrue(preferences.all.isEmpty())
    }
}
