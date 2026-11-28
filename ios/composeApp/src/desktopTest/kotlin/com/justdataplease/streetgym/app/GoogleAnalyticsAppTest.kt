package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.ConsentKind
import com.justdataplease.streetgym.app.data.ExamQuestion
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.GoogleAnalytics
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.data.PhoneServices
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.UmpState
import com.justdataplease.streetgym.app.data.UmpStatus
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.PROGRESS_HISTORY
import com.justdataplease.streetgym.app.ui.PROGRESS_LEADERBOARD
import com.justdataplease.streetgym.app.ui.PROGRESS_SKILLS
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.analyticsScreenName
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.Placement
import com.justdataplease.streetgym.data.skills.SkillLevel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Google Analytics from the app's own places (Android 2.15.0: HomeActivity's trackScreen, startPlacement, startWorkout,
 * askEffortAndFinish and askExam, PlacementView, ShareProgressView, TiersPanel and ConsentWatcher), over a fake Firebase
 * Analytics and fake Google ads: the consent from Google's form at launch, after the form and when the app comes back;
 * every event where Android sends it, with Android's screen names; and the Info.plist's defaults as Android's manifest.
 */
@OptIn(ExperimentalTestApi::class)
class GoogleAnalyticsAppTest {
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L // 18 Nov 2026, 12:00 UTC

    private inner class Phone(tier: Tier = Tier.FREE, firebase: Boolean = true, ump: UmpState = UmpState.UNKNOWN, placed: Boolean = true) {
        val store = MemoryStore()
        val sink = FakeAnalyticsSink()
        val ads = FakeAds().apply { this.ump = ump }
        val model = AppSettings(store).let { settings ->
            settings.placementDone = placed
            AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = settings, library = LibraryStore(store),
                plans = Plans(store, FakeStore(), floor = tier), ads = ads, adStore = store,
                services = PhoneServices(analytics = if (firebase) sink else null))
        }

        /** Today's class, every set done; with [logged], each set logged at that many reps or seconds. */
        fun train(logged: Int? = null) {
            model.start(model.today().next!!.id)
            while (true) {
                val state = model.training.state()!!
                val index = (state.page as? FocusPage.Set)?.index ?: break
                time += 30_000L
                logged?.let { model.training.setDone(state.flow[index].key, it) }
                model.training.done(index)
                model.training.skipRest()
            }
            model.training.finish(7)
        }
    }

    // ---- Consent

    @Test
    fun europeWaitsForTheFormAndFollowsItsAnswers() {
        val phone = Phone()
        // A first launch: collection off before anything is known.
        assertEquals(listOf(false), phone.sink.collection)
        // The app opens: UMP knows nothing yet; asked once, it says the form must be answered.
        phone.ads.updated = UmpState(UmpStatus.REQUIRED, null, 1)
        phone.model.opened()
        assertEquals(1, phone.ads.consentInfoUpdates)
        assertEquals(listOf(DENIED), phone.sink.consents)
        assertFalse(phone.model.googleAnalytics.collecting)

        // Free's first card raises Google's form: analytics yes, ads personalisation no.
        phone.ads.answered = UmpState(UmpStatus.OBTAINED, "1121", 1)
        phone.model.adConsent.gather()
        assertEquals(mapOf(ConsentKind.AD_STORAGE to true, ConsentKind.AD_USER_DATA to true, ConsentKind.AD_PERSONALIZATION to false,
            ConsentKind.ANALYTICS_STORAGE to true), phone.sink.consents.last())
        assertEquals(listOf(false, true), phone.sink.collection)
        assertEquals("true", phone.store.get("streetgym_google_analytics.analytics_storage_granted"))

        // Back in the app: decided again, UMP not asked again this launch.
        phone.model.resumed()
        assertEquals(1, phone.ads.consentInfoUpdates)
        assertEquals(2, phone.sink.consents.size)

        // Settings › About › Ad privacy: analytics withdrawn, collection stops.
        phone.ads.privacy = com.justdataplease.streetgym.app.data.AdPrivacy.SHOWN
        phone.ads.answered = UmpState(UmpStatus.OBTAINED, "1112", 1)
        phone.model.adConsent.showPrivacy {}
        assertEquals(listOf(false, true, false), phone.sink.collection)

        // A choice changed while away (UMP's keys), seen when the app comes back.
        phone.ads.ump = UmpState(UmpStatus.OBTAINED, "1111", 1)
        phone.model.resumed()
        assertEquals(listOf(false, true, false, true), phone.sink.collection)
    }

    @Test
    fun outsideEuropePlusIsGrantedWithoutAnyAdAndTheNextLaunchStartsOn() {
        val phone = Phone(Tier.PLUS)
        phone.ads.updated = UmpState(UmpStatus.NOT_REQUIRED, null, null)
        phone.model.opened()
        assertEquals(listOf(GRANTED), phone.sink.consents)
        assertTrue(phone.model.googleAnalytics.collecting)
        // Plus and Pro see no form and no ad: only the consent information was asked.
        assertTrue(phone.ads.steps.isEmpty())
        // The next launch collects from its start, before UMP answers (Firebase keeps the consent too).
        val next = AppModel(program, StoredProgress(phone.store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(phone.store),
            adStore = phone.store, services = PhoneServices(analytics = FakeAnalyticsSink()))
        assertTrue(next.googleAnalytics.collecting)
    }

    @Test
    fun withoutFirebaseNothingIsAskedOrSent() {
        val phone = Phone(firebase = false, ump = UmpState(UmpStatus.NOT_REQUIRED, null, null))
        phone.model.opened()
        phone.model.startPlacement(Placement.Start(1, 1))
        phone.train()
        assertEquals(0, phone.ads.consentInfoUpdates)
        assertFalse(phone.model.googleAnalytics.available)
        assertTrue(phone.sink.collection.isEmpty() && phone.sink.events.isEmpty())
        assertEquals(null, phone.store.get("streetgym_google_analytics.analytics_storage_granted"))
    }

    // ---- The events

    @Test
    fun theStrengthTestClassesExamsSharingAndOffersSendAndroidsEvents() {
        val phone = Phone(placed = false, ump = UmpState(UmpStatus.NOT_REQUIRED, null, null))
        val model = phone.model
        val sink = phone.sink
        model.opened()
        assertTrue(model.googleAnalytics.collecting)

        // Skipped: tutorial_complete at Level 1, class 1.
        model.startPlacement(Placement.Start(1, 1))
        assertEquals(GoogleAnalytics.TUTORIAL_COMPLETE to mapOf<String, Any>("start_level" to 1L, "start_class" to 1L, "method" to "skip"),
            sink.events.last())

        // A class: class_start when it starts (not when resumed), class_complete with its minutes.
        val first = model.today().next!!
        val started = time
        phone.train()
        assertEquals(listOf(GoogleAnalytics.TUTORIAL_COMPLETE, GoogleAnalytics.CLASS_START, GoogleAnalytics.CLASS_COMPLETE),
            sink.names.filter { it != GoogleAnalytics.SKILL_RANK_UP })
        assertEquals(mapOf<String, Any>("workout_type" to "class", "level" to 1L, "class_number" to first.number.toLong(),
            "intensity" to "normal", "repeat" to "false"), sink.events.first { it.first == GoogleAnalytics.CLASS_START }.second)
        val complete = sink.events.first { it.first == GoogleAnalytics.CLASS_COMPLETE }.second
        assertEquals((time - started) / 60_000L, complete["duration_min"])
        assertEquals("class", complete["workout_type"])

        // Each rank a finished class raises, Beginner up, by the rung that earned it: strong sets, until a rank comes.
        var raised = sink.events.count { it.first == GoogleAnalytics.SKILL_RANK_UP }
        repeat(20) {
            if (raised > 0) return@repeat
            val before = model.skillProgress()!!.statuses.associate { it.skill.id to it.level }
            val logged = sink.events.size
            phone.train(logged = 60)
            val expected = model.skillProgress()!!.statuses
                .filter { it.carriedBy == null && it.level > (before[it.skill.id] ?: it.level) }
                .map { GoogleAnalytics.SKILL_RANK_UP to mapOf<String, Any>("skill_id" to it.skill.id, "rank" to it.level.name.lowercase()) }
            assertEquals(expected, sink.events.drop(logged).filter { it.first == GoogleAnalytics.SKILL_RANK_UP })
            raised += expected.size
        }
        assertTrue(raised > 0, "no rank in 20 classes")
        assertTrue(sink.events.filter { it.first == GoogleAnalytics.SKILL_RANK_UP }.none { it.second["rank"] == SkillLevel.NONE.name.lowercase() })

        // A repeat of a completed class, then resuming it, counts one start.
        val starts = sink.names.count { it == GoogleAnalytics.CLASS_START }
        model.start(first.id, repeat = true)
        model.start(first.id, repeat = true)
        assertEquals(starts + 1, sink.names.count { it == GoogleAnalytics.CLASS_START })
        assertEquals("true", sink.events.last().second["repeat"])
        model.training.abandon()

        // An exam's targets saved.
        val exam = ordered.first { it.isExam }
        model.saveExam(ExamQuestion(exam, exam.exercises.map { it to true }, offerRepeat = true), exam.exercises.map { true })
        assertEquals(GoogleAnalytics.EXAM_COMPLETE to mapOf<String, Any>("level" to exam.levelNumber.toLong(),
            "class_number" to exam.number.toLong(), "passed" to "true"), sink.events.last())

        // An offer, as each lock opens it: what was tapped and the plan it leads with.
        model.offerShown(Paywall.Level(4))
        model.offerShown(Paywall.Level(7))
        model.offerShown(Paywall.Ads)
        assertEquals(listOf(
            mapOf<String, Any>("source" to "level", "tier" to "plus"),
            mapOf<String, Any>("source" to "level", "tier" to "pro"),
            mapOf<String, Any>("source" to "remove_ads", "tier" to "plus"),
        ), sink.events.takeLast(3).map { it.second })

        // Who is looking: Free, the next class's level, the theme.
        model.syncUserProperties()
        assertEquals(listOf<Pair<String, String?>>("tier" to "free", "level" to "1", "theme" to "concrete"), sink.properties.takeLast(3))

        // Share my progress off stops it all; on starts it again.
        model.shareProgress = false
        assertFalse(model.googleAnalytics.collecting)
        val logged = sink.events.size
        phone.train()
        assertEquals(logged, sink.events.size)
        model.shareProgress = true
        assertTrue(model.googleAnalytics.collecting)
    }

    @Test
    fun theStrengthTestsMethodIsTheTestOrALevelTest() {
        for ((skill, method) in listOf(0 to GoogleAnalytics.METHOD_TEST, 3 to GoogleAnalytics.METHOD_SKILL)) {
            val phone = Phone(Tier.PRO, placed = false, ump = UmpState(UmpStatus.NOT_REQUIRED, null, null))
            phone.model.opened()
            val form = phone.model.placementForm
            listOf(20, 50, 75).forEachIndexed(form::set)
            form.calculate()
            form.skill = skill
            val start = phone.model.placementResult()!!.start
            phone.model.startPlacement(start)
            assertEquals(GoogleAnalytics.TUTORIAL_COMPLETE to mapOf<String, Any>("start_level" to start.level.toLong(),
                "start_class" to start.day.toLong(), "method" to method), phone.sink.events.last())
        }
    }

    /** Android's HomeActivity.screenName, every screen and tab. */
    @Test
    fun screenNamesAreAndroidsExactly() {
        fun name(screen: Screen, placing: Boolean = false, skills: Boolean = true, training: Int = 0, progress: Int = PROGRESS_SKILLS) =
            analyticsScreenName(screen, placing, skills, training, progress)
        assertEquals("today", name(Screen.Today))
        assertEquals("strength_test", name(Screen.Today, placing = true))
        assertEquals(listOf("levels", "my_workouts", "library", "parks"), (0..3).map { name(Screen.Training, training = it) })
        assertEquals("level_classes", name(Screen.Level(4)))
        assertEquals("class_overview", name(Screen.Class("sg:l01:p0001")))
        assertEquals("workout", name(Screen.Workout("sg:l01:p0001")))
        assertEquals("workout_editor", name(Screen.EditDay("custom:a")))
        assertEquals(listOf("skills", "history", "leaderboard"),
            listOf(PROGRESS_SKILLS, PROGRESS_HISTORY, PROGRESS_LEADERBOARD).map { name(Screen.Progress, progress = it) })
        assertEquals("history", name(Screen.Progress, skills = false, progress = PROGRESS_LEADERBOARD))
        assertEquals("settings", name(Screen.Settings))
        assertEquals("timer", name(Screen.Timer))
    }

    /** The screens by taps: each screen and tab once, the strength test's first count, an offer and its buy buttons. */
    @Test
    fun theAppsScreensTheTestAndTheOffersAreCountedAsTheAthleteGoes() = runComposeUiTest {
        val phone = Phone(placed = false, ump = UmpState(UmpStatus.NOT_REQUIRED, null, null))
        val sink = phone.sink
        setContent { StreetGymApp(phone.model, PaddingValues()) }
        waitForIdle()
        // Opened: granted outside Europe, then the strength test on view.
        assertEquals(listOf(false, true), sink.collection)
        assertEquals(listOf<Any?>("strength_test"), sink.screens)
        assertEquals(listOf<Pair<String, String?>>("tier" to "free", "level" to "1", "theme" to "concrete"), sink.properties)

        // The first count typed begins it, once (counts that start past Level 1, so Start from scratch is offered).
        onNodeWithContentDescription("Strict pull-ups").performTextInput("10")
        onNodeWithContentDescription("Push-ups").performTextInput("30")
        onNodeWithContentDescription("Bodyweight squats").performTextInput("0")
        assertEquals(1, sink.names.count { it == GoogleAnalytics.TUTORIAL_BEGIN })
        onNodeWithText("CALCULATE").performClick()
        onNodeWithText("Start from scratch").performScrollTo().performClick()
        waitForIdle()
        assertEquals(GoogleAnalytics.TUTORIAL_COMPLETE to mapOf<String, Any>("start_level" to 1L, "start_class" to 1L, "method" to "test"),
            sink.events.first { it.first == GoogleAnalytics.TUTORIAL_COMPLETE })
        assertEquals("today", sink.screens.last())

        // Each tab once; going back to one counts it again.
        for (tab in listOf("Training", "Progress", "Timer", "Settings", "Today")) {
            onNodeWithContentDescription(tab).performClick()
            waitForIdle()
        }
        assertEquals(listOf<Any?>("strength_test", "today", "levels", "skills", "timer", "settings", "today"), sink.screens)

        // Remove ads: the offer's view, then a buy button's tap with what opened it.
        onNodeWithContentDescription("Remove ads").performClick()
        waitForIdle()
        assertEquals(GoogleAnalytics.PAYWALL_VIEW to mapOf<String, Any>("source" to "remove_ads", "tier" to "plus"), sink.events.last())
        onNodeWithText("GET PLUS").performScrollTo().performClick()
        waitForIdle()
        assertTrue(GoogleAnalytics.PAYWALL_TAP to mapOf<String, Any>("source" to "remove_ads", "tier" to "plus", "plan" to "plus") in sink.events)
        // Plus bought: the tier's user property follows.
        assertEquals<Pair<String, String?>>("tier" to "plus", sink.properties.last { it.first == "tier" })
        onNodeWithText("CANCEL").performClick()

        // Settings' plans: its source is "settings".
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("GET PRO FOR LIFE").performScrollTo().performClick()
        waitForIdle()
        assertEquals(GoogleAnalytics.PAYWALL_TAP to mapOf<String, Any>("source" to "settings", "tier" to "pro", "plan" to "pro_life"),
            sink.events.last { it.first == GoogleAnalytics.PAYWALL_TAP })
    }

    /** Nothing before consent is known: the Info.plist's defaults are Android's manifest's, key for key. */
    @Test
    fun theInfoPlistStartsWithCollectionOffAsAndroidsManifest() {
        val manifest = listOf("../../android-streetgym/app/src/main/AndroidManifest.xml", "../../app/src/main/AndroidManifest.xml")
            .map(::File).first { it.isFile }.readText()
        val android = Regex("""<meta-data android:name="((?:firebase|google)_analytics_[a-z_]+)" android:value="(true|false)" />""")
            .findAll(manifest).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(6, android.size)
        val plist = File("../iosApp/project.yml").readText()
        android.forEach { (key, value) ->
            // The same names in capitals on iPhone; automatic screen reporting has Firebase's own iPhone key.
            val ios = if (key == "google_analytics_automatic_screen_reporting_enabled") "FirebaseAutomaticScreenReportingEnabled" else key.uppercase()
            assertTrue(Regex("""\n\s+$ios: $value\n""").containsMatchIn(plist), "$ios: $value")
        }
        assertTrue("product: FirebaseAnalytics" in plist)
    }

    private companion object {
        val GRANTED = ConsentKind.entries.associateWith { true }
        val DENIED = ConsentKind.entries.associateWith { false }
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
