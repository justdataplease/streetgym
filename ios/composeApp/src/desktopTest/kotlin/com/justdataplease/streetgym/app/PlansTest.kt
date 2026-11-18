package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.Bought
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Offers
import com.justdataplease.streetgym.app.data.Ownership
import com.justdataplease.streetgym.app.data.PaymentEvidence
import com.justdataplease.streetgym.app.data.PaymentState
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.Products
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.Restored
import com.justdataplease.streetgym.app.data.Store
import com.justdataplease.streetgym.app.data.StoreStatus
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.data.reconcileEntitlement
import com.justdataplease.streetgym.app.ui.PRIVACY_URL
import com.justdataplease.streetgym.app.ui.PROGRESS_LEADERBOARD
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.Shown
import com.justdataplease.streetgym.app.ui.TERMS_URL
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.NextClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plans and purchases (Android: TiersPanel, TierBilling, Entitlement and showPaywall) over a fake store at Android's
 * prices: each lock's offer in Android's words with only the plans that open it, buying Plus, Pro for life and the
 * yearly Pro and what each opens everywhere, a payment waiting, restoring, a refund and a lapsed yearly Pro, the plan
 * cached for offline use under Android's keys, Simulate tier, the store's links and sheets, and Free's leaderboard.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PlansTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private val time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun model(store: MemoryStore = MemoryStore(), purchases: Store = FakeStore(), simulate: Boolean = false, done: Int = 0): AppModel {
        AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store),
            library = LibraryStore(store), plans = Plans(store, purchases, canSimulate = simulate)).also { model ->
            if (done > 0) model.progress.completePrograms(ordered.take(done).map { it.id }, at = time - 86_400_000L)
        }
    }

    // ---- The rules

    @Test
    fun theProductsAreAndroidsAndTheCacheKeepsAndroidsNames() {
        assertEquals(listOf("plus", "pro_life", "pro"), Products.ALL)
        assertEquals("streetgym_billing.plus_owned", Plans.PLUS_OWNED)
        assertEquals("streetgym_billing.pro_active", Plans.PRO_ACTIVE)
        assertEquals("streetgym_billing.pro_life_owned", Plans.PRO_LIFE_OWNED)
        assertEquals("streetgym_billing.simulated_tier", Plans.SIMULATED_TIER)
    }

    /** Android's EntitlementTest, on the iPhone's copy of reconcileEntitlement. */
    @Test
    fun onlyAConfirmedPurchaseGrantsAndOnlyACompleteAnswerTakesAway() {
        fun payment(state: PaymentState, product: String = "plus") = listOf(PaymentEvidence(product, state))
        assertEquals(Ownership(true, false), reconcileEntitlement("plus", false, payment(PaymentState.Purchased), false))
        assertEquals(Ownership(false, true), reconcileEntitlement("plus", false, payment(PaymentState.Pending), true))
        assertEquals(Ownership(false, false), reconcileEntitlement("plus", false, listOf(PaymentEvidence("full_program", PaymentState.Purchased)), true))
        assertTrue(reconcileEntitlement("plus", true, emptyList(), false).owned)
        assertTrue(reconcileEntitlement("plus", true, payment(PaymentState.Pending), false).owned)
        assertFalse(reconcileEntitlement("plus", true, emptyList(), true).owned)
        assertFalse(reconcileEntitlement("pro", true, emptyList(), true).owned)
        assertTrue(reconcileEntitlement("pro", false, payment(PaymentState.Purchased, "pro"), true).owned)
        assertFalse(reconcileEntitlement("plus", false, payment(PaymentState.Purchased, "pro"), true).owned)
        assertFalse(reconcileEntitlement("plus", false, payment(PaymentState.Other), false).owned)
    }

    /** Which plans each offer shows and its tag (Android: TiersPanel's proOnly, badge and render). */
    @Test
    fun eachOfferShowsOnlyThePlansThatOpenIt() {
        val all = listOf(Paywall.Level(4), Paywall.Level(7), Paywall.Ready, Paywall.Parks, Paywall.Leaderboard, Paywall.Saved(3),
            Paywall.Custom, Paywall.Lists(0), Paywall.Lists(3), Paywall.Ads)
        assertEquals(setOf<Paywall>(Paywall.Level(7), Paywall.Ready), all.filter(Offers::proOnly).toSet())
        // Free: Plus and Pro, except where only Pro opens it; the tag says which.
        all.forEach { paywall ->
            val proOnly = Offers.proOnly(paywall)
            assertEquals(!proOnly, Offers.showsPlus(paywall, Tier.FREE), "$paywall")
            assertTrue(Offers.showsPro(paywall, Tier.FREE))
            assertEquals(if (paywall == Paywall.Ads) null else if (proOnly) Tier.PRO else Tier.PLUS, Offers.badge(paywall, Tier.FREE))
        }
        // Past Plus an offer has only Pro; with Pro, none. Settings always shows both, with no tag.
        all.forEach { assertFalse(Offers.showsPlus(it, Tier.PLUS)) }
        assertEquals(Tier.PRO, Offers.badge(Paywall.Lists(3), Tier.PLUS))
        all.forEach { assertFalse(Offers.showsPro(it, Tier.PRO)) }
        Tier.entries.forEach { tier ->
            assertTrue(Offers.showsPlus(null, tier) && Offers.showsPro(null, tier))
            assertNull(Offers.badge(null, tier))
        }
    }

    @Test
    fun thePlanIsCachedForOfflineUseAndOnlyTheStoresCompleteAnswerTakesItAway() {
        val store = MemoryStore()
        store.set(Plans.PRO_LIFE_OWNED, "true")
        val offline = FakeStore(reachable = false)
        val plans = Plans(store, offline)
        // Opened offline: Pro at once, and it stays though the store says nothing.
        assertEquals(Tier.PRO, plans.tier)
        plans.connect()
        assertEquals(StoreStatus.UNAVAILABLE, offline.status.value)
        assertEquals(Tier.PRO, plans.tier)
        // Back online, a complete answer without it: refunded, so it goes, and the cache says so.
        offline.reachable = true
        plans.connect()
        plans.reconcile(offline.entitlements.value!!)
        assertEquals(Tier.FREE, plans.tier)
        assertEquals("false", store.get(Plans.PRO_LIFE_OWNED))
        // A new install of a buyer: the store's answer brings Plus back, and the cache keeps it from then on.
        val reinstalled = MemoryStore()
        val owner = FakeStore().apply { owns(Products.PLUS) }
        val again = Plans(reinstalled, owner)
        assertEquals(Tier.FREE, again.tier)
        again.connect()
        again.reconcile(owner.entitlements.value!!)
        assertEquals(Tier.PLUS, again.tier)
        assertEquals(Tier.PLUS, Plans(reinstalled, FakeStore(reachable = false)).tier)
    }

    @Test
    fun anExpiredYearlyProFallsBackToWhatElseWasBought() = runTest {
        val store = MemoryStore()
        val fake = FakeStore()
        val plans = Plans(store, fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { plans.follow() }
        plans.connect()
        plans.buy(Products.PLUS)
        plans.buy(Products.PRO)
        assertEquals(Tier.PRO, plans.tier)
        assertTrue(plans.proActive)
        assertEquals("true", store.get(Plans.PRO_ACTIVE))
        // The year ran out without renewing: Plus is left; then a refund of Plus: Free. Nothing else is deleted.
        fake.lapse()
        assertEquals(Tier.PLUS, plans.tier)
        assertEquals("false", store.get(Plans.PRO_ACTIVE))
        fake.refund(Products.PLUS)
        assertEquals(Tier.FREE, plans.tier)
    }

    @Test
    fun aPendingPaymentUnlocksNothingUntilItIsConfirmed() = runTest {
        val fake = FakeStore().apply { next = Bought.PENDING }
        val plans = Plans(MemoryStore(), fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { plans.follow() }
        plans.connect()
        var ended: Bought? = null
        plans.buy(Products.PLUS) { ended = it }
        assertEquals(Bought.PENDING, ended)
        assertTrue(plans.plusPending)
        assertEquals(Tier.FREE, plans.tier)
        fake.approve(Products.PLUS)
        assertFalse(plans.plusPending)
        assertEquals(Tier.PLUS, plans.tier)
    }

    @Test
    fun restoreSaysWhatItFound() {
        fun restored(fake: FakeStore): Restored {
            val plans = Plans(MemoryStore(), fake)
            plans.connect()
            var result: Restored? = null
            plans.restore { result = it }
            return result!!
        }
        assertEquals(Restored.PLUS, restored(FakeStore().apply { owns(Products.PLUS) }))
        assertEquals(Restored.PRO, restored(FakeStore().apply { owns(Products.PLUS, Products.PRO_LIFE) }))
        assertEquals(Restored.PRO, restored(FakeStore().apply { owns(Products.PRO) }))
        assertEquals(Restored.NOT_OWNED, restored(FakeStore()))
        assertEquals(Restored.UNAVAILABLE, restored(FakeStore(reachable = false)))
    }

    @Test
    fun simulateTierRaisesTheTierOnlyWhereItIsOffered() {
        val store = MemoryStore()
        val plans = Plans(store, canSimulate = true)
        assertEquals(Tier.FREE, plans.tier)
        plans.simulate(Tier.PRO)
        assertEquals(Tier.PRO, plans.tier)
        assertEquals("PRO", store.get(Plans.SIMULATED_TIER))
        // Kept for the next start; a release build ignores it.
        assertEquals(Tier.PRO, Plans(store, canSimulate = true).tier)
        assertEquals(Tier.FREE, Plans(store, canSimulate = false).tier)
        // A simulated Free never lowers what was bought (Android: aSimulatedTierStillCountsCachedOwnership).
        store.set(Plans.PLUS_OWNED, "true")
        val owner = Plans(store, canSimulate = true)
        owner.simulate(Tier.FREE)
        assertEquals(Tier.PLUS, owner.tier)
        owner.simulate(null)
        assertNull(store.get(Plans.SIMULATED_TIER))
        assertEquals(Tier.PLUS, owner.tier)
    }

    // ---- The screens

    @Test
    fun eachLockOpensItsOfferInAndroidsWords() = runComposeUiTest {
        val free = model(done = 300)
        var shown by mutableStateOf<Paywall>(Paywall.Level(4))
        setContent { key(shown) { StreetGymApp(free, PaddingValues(), showing = Shown.Offer(shown)) } }

        // Level 4: Plus, its lever line, both plans at Android's prices.
        onNodeWithText("Level 4 is part of Plus", ignoreCase = true).assertExists()
        onNodeWithText("Lever, planche, flag and handstand work. Pro includes it too, and adds Levels 7 to 10.").assertExists()
        onNodeWithContentDescription("Plus").assertExists()
        onNodeWithText("€14.99").assertExists()
        onNodeWithText("GET PLUS").performScrollTo().assertIsEnabled()
        onNodeWithText("€69.99").performScrollTo().assertExists()
        onNodeWithText("GET PRO FOR LIFE").performScrollTo().assertIsEnabled()
        onNodeWithText("OR €29.99 A YEAR").performScrollTo().assertIsEnabled()
        onNodeWithText("Yearly renews until you cancel it in the App Store.").performScrollTo().assertExists()
        onNodeWithText("Terms of Use").performScrollTo().assertExists()
        onNodeWithText("Privacy Policy").assertExists()
        onNodeWithText("RESTORE PURCHASES").performScrollTo().assertExists()
        onNodeWithText("REDEEM A PROMO CODE").performScrollTo().assertExists()
        onNodeWithText("CANCEL").assertExists()
        // Settings' line about the current plan is for Settings only.
        onNodeWithText("You’re on Free: Levels 1–3, with ads.").assertDoesNotExist()

        // Level 7 and the ready workouts: Pro alone.
        shown = Paywall.Level(7)
        waitForIdle()
        onNodeWithText("Level 7 is part of Pro", ignoreCase = true).assertExists()
        onNodeWithText("Levels 7 to 10 build up to the full front lever, planche, human flag, Hefesto, one-arm pull-up and one-arm handstand.").assertExists()
        onNodeWithContentDescription("Pro").assertExists()
        onNodeWithText("GET PLUS").assertDoesNotExist()
        onNodeWithText("GET PRO FOR LIFE").performScrollTo().assertExists()
        shown = Paywall.Ready
        waitForIdle()
        onNodeWithText("Ready workouts are part of Pro", ignoreCase = true).assertExists()
        onNodeWithText("Workouts for days off the program: pull, push, legs, core, skills, short days, mobility, and aches and posture.").assertExists()
        onNodeWithText("GET PLUS").assertDoesNotExist()

        // The others: their heading, both plans, no line.
        listOf(
            Paywall.Parks to "Parks near you are part of Plus",
            Paywall.Leaderboard to "The leaderboard is part of Plus",
            Paywall.Saved(3) to "You’ve used all 3 saved and custom days",
            Paywall.Custom to "Your own workouts are part of Plus",
            Paywall.Lists(0) to "Your own lists are part of Plus",
        ).forEach { (paywall, heading) ->
            shown = paywall
            waitForIdle()
            onNodeWithText(heading, ignoreCase = true).assertExists()
            onNodeWithContentDescription("Plus").assertExists()
            onNodeWithText("GET PLUS").performScrollTo().assertExists()
            onNodeWithText("GET PRO FOR LIFE").performScrollTo().assertExists()
        }

        // Remove ads locks nothing: no tag, its line, both plans.
        shown = Paywall.Ads
        waitForIdle()
        onNodeWithText("Remove ads", ignoreCase = true, useUnmergedTree = true).assertExists()
        onNodeWithText("Plus ends the ads with one payment. Pro does too, and adds Levels 7 to 10.").assertExists()
        onNodeWithContentDescription("Plus").assertDoesNotExist()
        onNodeWithText("GET PLUS").performScrollTo().assertExists()
    }

    @Test
    fun plusMadeAllItsListsOffersOnlyPro() = runComposeUiTest {
        // Plus cached on the phone, the store not answering.
        val plus = model(MemoryStore().apply { set(Plans.PLUS_OWNED, "true") }, purchases = Store.None)
        setContent { StreetGymApp(plus, PaddingValues(), startAt = Screen.Training, trainingTab = 1, showing = Shown.Offer(Paywall.Lists(3))) }
        onNodeWithText("You’ve made all 3 lists of Plus", ignoreCase = true).assertExists()
        onNodeWithContentDescription("Pro").assertExists()
        onNodeWithText("GET PLUS").assertDoesNotExist()
        onNodeWithText("GET PRO FOR LIFE").performScrollTo().assertExists()
    }

    @Test
    fun buyingPlusFromTodaysUnlockOpensLevelsFourToSixEverywhere() = runComposeUiTest {
        val store = MemoryStore()
        val fake = FakeStore()
        val model = model(store, fake, done = 300)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Today) }

        assertTrue(model.today().locked)
        onNodeWithContentDescription("Remove ads").assertExists()
        onNodeWithContentDescription("Unlock Plus").performScrollTo().performClick()
        onNodeWithText("Level 4 is part of Plus", ignoreCase = true).assertExists()
        onNodeWithText("GET PLUS").performScrollTo().performClick()

        // Bought: cached under Android's key, and the offer now has only Pro left, tagged Pro.
        assertEquals(listOf("plus"), fake.asked)
        assertEquals("true", store.get(Plans.PLUS_OWNED))
        assertEquals(Tier.PLUS, model.tier)
        onNodeWithText("GET PLUS").assertDoesNotExist()
        onNodeWithContentDescription("Pro").assertExists()
        onNodeWithText("CANCEL").performClick()

        // Today trains Level 4's class; no ads, so no Remove ads; Level 4 opens, Level 7 stays Pro.
        assertFalse(model.today().locked)
        onNodeWithText("START WORKOUT").performScrollTo().assertExists()
        onNodeWithContentDescription("Unlock Plus").assertDoesNotExist()
        onNodeWithContentDescription("Remove ads").assertDoesNotExist()
        assertFalse(model.levelLocked(4) || model.levelLocked(6))
        assertTrue(model.levelLocked(7))
        assertTrue(model.tier.parks && model.tier.customDays && model.tier.lists && !model.tier.readyWorkouts)
        onNodeWithContentDescription("Training").performClick()
        val levels = program.catalog.levels
        onNodeWithContentDescription("${levels[3].name.resolve()}, 0 of 100 complete").performScrollTo().assertExists()
        onNodeWithContentDescription("${levels[6].name.resolve()}, 0 of 100 complete, Pro").performScrollTo().assertExists()
    }

    @Test
    fun proForLifeAndTheYearlyProOpenEverythingAndSettingsSaysWhichIsOwned() = runComposeUiTest {
        val store = MemoryStore()
        val fake = FakeStore()
        val model = model(store, fake)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }

        onNodeWithText("Plus and Pro", ignoreCase = true).assertExists()
        onNodeWithText("You’re on Free: Levels 1–3, with ads.").assertExists()
        onNodeWithText("OR €29.99 A YEAR").performScrollTo().performClick()
        assertEquals(Tier.PRO, model.tier)
        assertTrue(model.tier.readyWorkouts && !model.levelLocked(10))
        assertEquals("true", store.get(Plans.PRO_ACTIVE))
        onNodeWithText("You have Pro: everything is open.").performScrollTo().assertExists()
        onNodeWithText("Pro is active.").performScrollTo().assertExists()
        onNodeWithText("GET PRO FOR LIFE").assertDoesNotExist()
        // Settings shows both plans with what's owned: Plus comes with Pro.
        onNodeWithText("Plus is yours.").performScrollTo().assertExists()
        onNodeWithText("MANAGE SUBSCRIPTION").performScrollTo().performClick()
        assertEquals(1, fake.managed)

        // The year runs out: Free again, the yearly offer back.
        fake.lapse()
        waitForIdle()
        assertEquals(Tier.FREE, model.tier)
        onNodeWithText("You’re on Free: Levels 1–3, with ads.").performScrollTo().assertExists()
        onNodeWithText("MANAGE SUBSCRIPTION").assertDoesNotExist()

        // Pro for life.
        onNodeWithText("GET PRO FOR LIFE").performScrollTo().performClick()
        assertEquals("true", store.get(Plans.PRO_LIFE_OWNED))
        assertEquals(Tier.PRO, model.tier)
        onNodeWithText("Pro is yours for life.").performScrollTo().assertExists()
    }

    @Test
    fun aPendingPaymentSaysSoAndAFailedOneAsksToTryAgain() = runComposeUiTest {
        val fake = FakeStore().apply { next = Bought.PENDING }
        val model = model(purchases = fake)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }
        onNodeWithText("GET PLUS").performScrollTo().performClick()
        onNodeWithText("Payment pending. It unlocks when the App Store confirms it.").performScrollTo().assertExists()
        assertEquals(Tier.FREE, model.tier)
        fake.approve(Products.PLUS)
        waitForIdle()
        assertEquals(Tier.PLUS, model.tier)
        onNodeWithText("Plus is yours.").performScrollTo().assertExists()

        fake.next = Bought.FAILED
        onNodeWithText("GET PRO FOR LIFE").performScrollTo().performClick()
        onNodeWithText("App Store purchases are unavailable. Try again later.").performScrollTo().assertExists()
        assertEquals(Tier.PLUS, model.tier)
    }

    @Test
    fun restorePurchasesSaysWhatItFoundInTheStoresWords() = runComposeUiTest {
        val fake = FakeStore()
        val model = model(purchases = fake)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }
        onNodeWithText("RESTORE PURCHASES").performScrollTo().performClick()
        onNodeWithText("No purchase found for this Apple Account.").performScrollTo().assertExists()
        fake.owns(Products.PLUS)
        onNodeWithText("RESTORE PURCHASES").performScrollTo().performClick()
        onNodeWithText("Plus restored. Levels 4 to 6 and the leaderboard are open, and ads are off.").performScrollTo().assertExists()
        assertEquals(Tier.PLUS, model.tier)
        fake.reachable = false
        onNodeWithText("RESTORE PURCHASES").performScrollTo().performClick()
        onNodeWithText("Couldn’t reach the App Store. Try again later.").performScrollTo().assertExists()
        fake.reachable = true
        fake.owns(Products.PRO_LIFE)
        onNodeWithText("RESTORE PURCHASES").performScrollTo().performClick()
        onNodeWithText("Pro restored. Everything is unlocked.").performScrollTo().assertExists()
    }

    @Test
    fun theButtonsWaitForTheStoresPrices() = runComposeUiTest {
        val loading = object : Store by Store.None {
            override val status: StateFlow<StoreStatus> = MutableStateFlow(StoreStatus.LOADING)
        }
        var store by mutableStateOf<Store>(loading)
        setContent {
            val model = androidx.compose.runtime.remember(store) { model(purchases = store) }
            StreetGymApp(model, PaddingValues(), startAt = Screen.Settings)
        }
        onNodeWithText("Connecting to the App Store…").performScrollTo().assertExists()
        onNodeWithText("GET PLUS").performScrollTo().assertIsNotEnabled()
        onNodeWithText("GET PRO FOR LIFE").performScrollTo().assertIsNotEnabled()
        // No price yet: the yearly button names none.
        onNodeWithText("OR PAY YEARLY").performScrollTo().assertIsNotEnabled()
        onNodeWithText("€14.99").assertDoesNotExist()
        store = FakeStore(catalog = emptyMap())
        waitForIdle()
        onNodeWithText("App Store purchases are unavailable.").performScrollTo().assertExists()
        onNodeWithText("GET PLUS").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun theTermsThePrivacyPolicyAndAnOfferCodeOpenFromThePlans() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val links = object : UriHandler {
            override fun openUri(uri: String) { opened += uri }
        }
        val fake = FakeStore().apply { code = Products.PRO_LIFE }
        val model = model(purchases = fake)
        setContent { CompositionLocalProvider(LocalUriHandler provides links) { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) } }
        onNodeWithText("Terms of Use").performScrollTo().performClick()
        onNodeWithText("Privacy Policy").performScrollTo().performClick()
        assertEquals(listOf(TERMS_URL, PRIVACY_URL), opened)
        assertEquals("https://www.apple.com/legal/internet-services/itunes/dev/stdeula/", TERMS_URL)
        // Apple's offer-code sheet; what it redeems arrives as a purchase.
        onNodeWithText("REDEEM A PROMO CODE").performScrollTo().performClick()
        assertEquals(1, fake.redeemed)
        waitForIdle()
        assertEquals(Tier.PRO, model.tier)
    }

    @Test
    fun simulateTierIsInTheDeveloperSettingsOfADebugBuild() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store, simulate = true)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings, showing = Shown.Developer) }
        onNodeWithText("Simulate tier", ignoreCase = true).performScrollTo().assertExists()
        onNodeWithText("Debug builds only. Default is what this phone bought. Pick a tier to test its limits and ads.").assertExists()
        onNodeWithText("Plus").performScrollTo().performClick()
        assertEquals(Tier.PLUS, model.tier)
        assertEquals("PLUS", store.get(Plans.SIMULATED_TIER))
        onNodeWithText("Default").performClick()
        assertEquals(Tier.FREE, model.tier)
        assertNull(store.get(Plans.SIMULATED_TIER))
    }

    @Test
    fun aReleaseBuildHasNoSimulateTier() = runComposeUiTest {
        setContent { StreetGymApp(model(simulate = false), PaddingValues(), startAt = Screen.Settings, showing = Shown.Developer) }
        onNodeWithText("Inspect all days").assertExists()
        onNodeWithText("Simulate tier", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun freesLeaderboardIsABoardUnderTheLayerAndPlusReadsItIsNotInThisBuild() = runComposeUiTest {
        val fake = FakeStore()
        val model = model(purchases = fake)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_LEADERBOARD) }
        onNodeWithText("Points from your training, all time and this month, worldwide and by country.").assertExists()
        onNodeWithContentDescription("Unlock Plus").performClick()
        onNodeWithText("The leaderboard is part of Plus", ignoreCase = true).assertExists()
        onNodeWithText("GET PLUS").performScrollTo().performClick()
        onNodeWithText("CANCEL").performClick()
        onNodeWithText("The leaderboard isn’t available in this build.").assertExists()
        onNodeWithContentDescription("Unlock Plus").assertDoesNotExist()
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
