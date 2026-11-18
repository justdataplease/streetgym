package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AdBreakClock
import com.justdataplease.streetgym.app.data.AdBreakPolicy
import com.justdataplease.streetgym.app.data.AdBreaks
import com.justdataplease.streetgym.app.data.AdConsent
import com.justdataplease.streetgym.app.data.AdPrivacy
import com.justdataplease.streetgym.app.data.AdRows
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.Products
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.Training
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.AD_PRIVACY_NONE
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.billing.Tier
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ads on Free (Android: WorkoutAdBreak, AdBreakClock, AdBreakPolicy, NativeAdOwner, ManagedNativePlacement and the
 * title bar's Remove ads) on a stepped clock: consent first, none on Plus or Pro, the five-minute rule at the next change
 * of screen, never in the first ten seconds, during a rest, a running Timer or over an offer; the cards where Android
 * puts them, Remove ads, Ad privacy, and a purchase taking every ad away at once.
 */
@OptIn(ExperimentalTestApi::class)
class AdsTest {
    private val minute = 60_000L

    /** The rules alone, over [ads], with the athlete's tier and phone given by the lambdas. */
    private class Rig(
        val ads: FakeAds = FakeAds(),
        var adFree: Boolean = false,
        var busy: Boolean = false,
        var resting: Boolean = false,
        val store: MemoryStore = MemoryStore(),
    ) {
        var clock = 0L
        val consent = AdConsent(ads) { adFree }
        val breaks = AdBreaks(ads, consent, store, { clock }, { adFree }, { busy }, { resting })

        /** Moves the clock on by [millis], ticking every second as the app does. */
        fun run(millis: Long) {
            val end = clock + millis
            while (clock < end) {
                clock = minOf(end, clock + 1_000L)
                breaks.tick()
            }
        }
    }

    // ---- Android's own checks of the clock and the policy

    @Test
    fun theGapIsFiveMinutesOfUseAndAFreshInstallWaitsItAll() {
        assertEquals(5 * 60_000L, AdBreakClock.GAP_MS)
        val clock = AdBreakClock()
        clock.resume(0)
        assertFalse(clock.due(299_999))
        assertTrue(clock.due(300_000))
        clock.shown(300_000)
        assertFalse(clock.due(599_999))
        assertTrue(clock.due(600_000))
        assertTrue(AdBreakPolicy.mayShow(adFree = false, due = true, resumed = true, timing = false, covered = false, consent = true))
        assertFalse(AdBreakPolicy.mayShow(adFree = true, due = true, resumed = true, timing = false, covered = false, consent = true))
        assertFalse(AdBreakPolicy.mayShow(adFree = false, due = true, resumed = true, timing = true, covered = false, consent = true))
        assertFalse(AdBreakPolicy.mayShow(adFree = false, due = true, resumed = true, timing = false, covered = true, consent = true))
        assertFalse(AdBreakPolicy.mayShow(adFree = false, due = true, resumed = true, timing = false, covered = false, consent = false))
        assertFalse(AdBreakPolicy.mayShow(adFree = false, due = true, resumed = false, timing = false, covered = false, consent = true))
        assertFalse(AdBreakPolicy.mayLoad(false, consent = true, elapsed = AdBreakClock.GAP_MS - 60_001))
        assertTrue(AdBreakPolicy.mayLoad(false, consent = true, elapsed = AdBreakClock.GAP_MS - 60_000))
        assertFalse(AdBreakPolicy.mayLoad(false, consent = false, elapsed = AdBreakClock.GAP_MS))
        assertFalse(AdBreakPolicy.mayLoad(true, consent = true, elapsed = AdBreakClock.GAP_MS))
    }

    @Test
    fun theListCardSitsAfterTheThirdRowAndSkillsAfterTheFourthLadder() {
        assertEquals(listOf(0, 1, 2, null, 3, 4), (0..5).map { AdRows.baseIndex(it, 100, AdRows.LEVEL_AFTER) })
        assertEquals(99, AdRows.baseIndex(100, 100, AdRows.LEVEL_AFTER))
        assertEquals(listOf(0, 1, null), (0..2).map { AdRows.baseIndex(it, 2, AdRows.LEVEL_AFTER) })
        assertEquals(3, AdRows.position(100, AdRows.LEVEL_AFTER))
        assertEquals(4, AdRows.position(9, AdRows.SKILLS_AFTER))
        assertEquals(2, AdRows.position(2, AdRows.SKILLS_AFTER))
    }

    // ---- Consent

    @Test
    fun consentComesFirstThenTrackingThenTheSdkAndNothingLoadsBefore() {
        val rig = Rig()
        rig.breaks.resume()
        // Ten minutes without consent: nothing loaded, nothing shown.
        rig.run(10 * minute)
        assertEquals(0, rig.ads.loads)
        assertFalse(rig.breaks.atBreak())
        rig.consent.gather()
        assertEquals(listOf("consent", "tracking", "start"), rig.ads.steps)
        assertTrue(rig.consent.canRequest && rig.consent.started)
        // Asked once while the app runs.
        rig.consent.gather()
        assertEquals(3, rig.ads.steps.size)
        rig.run(1_000L)
        assertEquals(1, rig.ads.loads)
        assertTrue(rig.breaks.atBreak())
    }

    @Test
    fun withoutConsentNoTrackingQuestionAndNoAds() {
        val rig = Rig(FakeAds(consent = false))
        rig.breaks.resume()
        rig.consent.gather()
        assertEquals(listOf("consent"), rig.ads.steps)
        rig.run(10 * minute)
        assertEquals(0, rig.ads.loads)
        assertFalse(rig.breaks.atBreak())
    }

    @Test
    fun plusAndProAreAskedNothingAndSeeNoFullScreenAd() {
        val rig = Rig(adFree = true)
        rig.breaks.resume()
        rig.consent.gather()
        assertTrue(rig.ads.steps.isEmpty())
        rig.run(20 * minute)
        assertEquals(0, rig.ads.loads)
        assertFalse(rig.breaks.atBreak())
    }

    // ---- The full-screen ad's breaks

    @Test
    fun atMostOneFullScreenAdEveryFiveMinutesOfUseAtTheNextBreak() {
        val rig = Rig()
        rig.consent.gather()
        rig.breaks.resume()
        // Loaded a minute before the gap ends, so it is ready at the next break.
        rig.run(4 * minute - 1_000L)
        assertEquals(0, rig.ads.loads)
        rig.run(1_000L)
        assertEquals(1, rig.ads.loads)
        rig.run(59_000L)
        assertFalse(rig.breaks.atBreak())
        rig.run(1_000L)
        assertTrue(rig.breaks.atBreak())
        assertEquals(1, rig.ads.shows)
        assertEquals("0", rig.store.get(AdBreaks.FOREGROUND_MS))
        // Up until it is closed, and then not again at once.
        assertFalse(rig.breaks.atBreak())
        rig.ads.close()
        assertFalse(rig.breaks.atBreak())
        // Missed breaks don't add up: twenty minutes later, one ad, then five minutes again.
        rig.run(20 * minute)
        assertTrue(rig.breaks.atBreak())
        rig.ads.close()
        assertFalse(rig.breaks.atBreak())
        rig.run(5 * minute)
        assertTrue(rig.breaks.atBreak())
        assertEquals(3, rig.ads.shows)
    }

    @Test
    fun neverInTheFirstTenSecondsNorOverARestATimerOrAnOffer() {
        val rig = Rig()
        rig.consent.gather()
        rig.breaks.resume()
        rig.run(6 * minute)
        // Away and back: the first ten seconds are the athlete's own.
        rig.breaks.pause()
        rig.breaks.resume()
        rig.run(9_000L)
        assertFalse(rig.breaks.atBreak())
        rig.run(1_000L)
        rig.resting = true
        assertFalse(rig.breaks.atBreak())
        rig.resting = false
        rig.busy = true
        assertFalse(rig.breaks.atBreak())
        rig.busy = false
        assertFalse(rig.breaks.atBreak(covered = true))
        assertEquals(0, rig.ads.shows)
        assertTrue(rig.breaks.atBreak())
    }

    @Test
    fun onlyTimeOnScreenCountsAndItIsKeptAcrossARestart() {
        val store = MemoryStore()
        val rig = Rig(store = store)
        rig.consent.gather()
        rig.breaks.resume()
        rig.run(3 * minute)
        rig.breaks.pause()
        assertEquals((3 * minute).toString(), store.get(AdBreaks.FOREGROUND_MS))
        // An hour in the background counts for nothing.
        rig.clock += 60 * minute
        rig.breaks.resume()
        rig.run(1 * minute + 30_000L)
        assertFalse(rig.breaks.atBreak())
        // The app closed and opened again: the three minutes are still there.
        rig.breaks.pause()
        val again = Rig(store = store).apply { clock = rig.clock }
        again.consent.gather()
        again.breaks.resume()
        again.run(30_000L + 10_000L)
        assertTrue(again.breaks.atBreak())
    }

    @Test
    fun buyingPlusLetsTheLoadedAdGoAndAnOldOneIsReplaced() {
        val rig = Rig()
        rig.consent.gather()
        rig.breaks.resume()
        rig.run(4 * minute)
        assertEquals(1, rig.ads.loads)
        // Never shown for 50 minutes (an offer over every break): let go and loaded again, as Google's expire in an hour.
        rig.run(50 * minute + 1_000L)
        assertEquals(1, rig.ads.drops)
        assertEquals(2, rig.ads.loads)
        rig.adFree = true
        rig.run(1_000L)
        assertEquals(2, rig.ads.drops)
        rig.run(20 * minute)
        assertEquals(2, rig.ads.loads)
        assertFalse(rig.breaks.atBreak())
    }

    @Test
    fun aFinishedSectionIsABreakOnceItsRestIsOver() {
        var time = 1_795_024_800_000L
        val store = MemoryStore()
        AppSettings(store).placementDone = true
        val model = AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store),
            plans = Plans(store), ads = FakeAds())
        val training = model.training
        val id = model.today().next!!.id
        model.start(id)
        val flow = training.state()!!.flow
        val warmUp = flow.keys(Training.WARM_UP).size
        assertTrue(warmUp > 1)
        repeat(warmUp) { set ->
            if (set > 0) assertFalse(training.takeSectionBreak())
            time += 40_000L
            training.skipRest()
            training.done((training.state()!!.page as FocusPage.Set).index)
        }
        // The warm-up is done; its break waits for the rest after its last set.
        if (training.resting()) {
            assertFalse(training.takeSectionBreak())
            training.skipRest()
        }
        assertTrue(training.takeSectionBreak())
        assertFalse(training.takeSectionBreak())
    }

    // ---- The screens

    private fun model(store: MemoryStore = MemoryStore(), ads: FakeAds = FakeAds(), purchases: FakeStore = FakeStore(), clock: () -> Long = { 0L }): AppModel {
        AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), now = { 1_795_024_800_000L }, zone = TimeZone.UTC, settings = AppSettings(store),
            plans = Plans(store, purchases), ads = ads, adStore = store, elapsed = clock)
    }

    @Test
    fun freeSeesTheCardClosingTodayAndRemoveAdsUntilPlusIsBought() = runComposeUiTest {
        val ads = FakeAds()
        val model = model(ads = ads)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Today) }
        waitForIdle()
        // Today asks for consent first, then the card closes the page.
        assertEquals(listOf("consent", "tracking", "start"), ads.steps)
        onNodeWithContentDescription(AD_STAND_IN).performScrollTo().assertExists()
        // Remove ads opens its offer: no tag, its line.
        onNodeWithContentDescription("Remove ads").performClick()
        onNodeWithText("Plus ends the ads with one payment. Pro does too, and adds Levels 7 to 10.").assertExists()
        onNodeWithText("GET PLUS").performScrollTo().performClick()
        onNodeWithText("CANCEL").performClick()
        // Bought: every card and the button go at once.
        onNodeWithContentDescription(AD_STAND_IN).assertDoesNotExist()
        onNodeWithContentDescription("Remove ads").assertDoesNotExist()
        assertTrue(model.plans.adFree)
    }

    @Test
    fun aWorkoutsCardsWaitForConsentAndTheFocusViewHasNone() = runComposeUiTest {
        val ads = FakeAds()
        val model = model(ads = ads)
        val id = model.today().next!!.id
        model.start(id)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(id)) }
        waitForIdle()
        // The focus view: no card, no Remove ads, and consent isn't asked mid-workout.
        onNodeWithContentDescription(AD_STAND_IN).assertDoesNotExist()
        onNodeWithContentDescription("Remove ads").assertDoesNotExist()
        assertTrue(ads.steps.isEmpty())
        // The list: still no consent asked, so no card.
        model.workoutView = WorkoutView.LIST
        waitForIdle()
        assertTrue(ads.steps.isEmpty())
        onNodeWithContentDescription(AD_STAND_IN).assertDoesNotExist()
        // Once Today has asked, the list shows its cards between sections and at the end.
        model.adConsent.gather()
        waitForIdle()
        assertTrue(onAllNodesWithContentDescription(AD_STAND_IN).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aLevelsClassesHaveACardAfterTheThirdAndSkillsAfterTheFourthLadder() = runComposeUiTest {
        val model = model()
        model.adConsent.gather()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Level(1)) }
        onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(AD_STAND_IN))
        onAllNodesWithContentDescription(AD_STAND_IN).onFirst().assertExists()
        // Skills: one partway down the ladders, one closing the page.
        onNodeWithContentDescription("Progress").performClick()
        assertEquals(2, onAllNodesWithContentDescription(AD_STAND_IN).fetchSemanticsNodes().size)
    }

    @Test
    fun adPrivacySaysWhenGoogleOffersNoChoices() = runComposeUiTest {
        val ads = FakeAds(privacy = AdPrivacy.NONE)
        setContent { StreetGymApp(model(ads = ads), PaddingValues(), startAt = Screen.Settings) }
        onNodeWithText("AD PRIVACY").performScrollTo().performClick()
        onNodeWithText(AD_PRIVACY_NONE).assertExists()
        assertEquals(1, ads.privacyShown)
    }

    @Test
    fun theFullScreenAdShowsAtTheNextChangeOfScreenButNotBetweenAWorkoutAndItsTimer() = runComposeUiTest {
        var clock = 0L
        val ads = FakeAds()
        val model = model(ads = ads, clock = { clock })
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Today) }
        waitForIdle()
        // Minutes of use, ticked every second as the shell does.
        fun use(minutes: Int) = repeat(minutes * 60) {
            clock += 1_000L
            model.adBreaks.tick()
        }
        use(5)
        assertEquals(1, ads.loads)
        // Starting the class is a change of screen: the ad.
        model.workoutView = WorkoutView.LIST
        onNodeWithText("START WORKOUT").performScrollTo().performClick()
        assertEquals(1, ads.shows)
        ads.close()
        // Five more minutes in the list, which keeps the tab bar: its Timer is part of the workout, no ad.
        use(6)
        onNodeWithContentDescription("Timer").performClick()
        assertEquals(1, ads.shows)
        // Any other change of tab is a break.
        onNodeWithContentDescription("Training").performClick()
        assertEquals(2, ads.shows)
        ads.close()
        // Not again until five more minutes.
        onNodeWithContentDescription("Progress").performClick()
        assertEquals(2, ads.shows)
        // Settings carries the plans, as an offer does: never there.
        use(6)
        onNodeWithContentDescription("Settings").performClick()
        assertEquals(2, ads.shows)
        onNodeWithContentDescription("Today").performClick()
        assertEquals(3, ads.shows)
        ads.close()
        // Plus bought: no more.
        model.plans.buy(Products.PLUS)
        use(6)
        onNodeWithContentDescription("Training").performClick()
        assertEquals(3, ads.shows)
        assertEquals(Tier.PLUS, model.tier)
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
