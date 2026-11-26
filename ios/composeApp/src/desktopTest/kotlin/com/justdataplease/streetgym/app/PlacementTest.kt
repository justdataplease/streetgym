package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.StrengthResult
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.figure.LocalReduceMotion
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.share_body
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.boldMarkup
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.Placement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first launch's strength test (Android: PlacementView, HomeActivity.askPlacement and startPlacement) over the real
 * program: the start and class several athletes get, checked against the engine's Placement; marking the classes
 * before it as placed; skipping; the screen by taps (counters, Calculate, the result, the skills step, a start held to
 * the tier, the keyboard's Done, the sharing switch); then the loading screen, the load error and the park on Today.
 */
@OptIn(ExperimentalTestApi::class)
class PlacementTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun model(store: MemoryStore = MemoryStore(), tier: Tier = Tier.PRO) =
        AppModel(program, StoredProgress(store), tier = tier, now = { time }, zone = TimeZone.UTC, settings = AppSettings(store))

    private fun where(start: Placement.Start) = if (start.day > 1) "Level ${start.level}, day ${start.day}" else "Level ${start.level}"

    @Test
    fun athletesStartWhereTheEnginePlacesThem() {
        val model = model()
        val starts = Placement.starts(ordered, TrainingProgram::levelNumber, TrainingProgram::number, TrainingProgram::isExam)
        assertEquals(starts, model.strengthTest.starts)
        // A beginner, the owner's case with and without squats, a strong athlete at the last free start.
        val athletes = listOf(Triple(0, 0, 0), Triple(10, 30, 0), Triple(10, 30, 75), Triple(8, 25, 40), Triple(3, 20, 40),
            Triple(15, 50, 75), Triple(20, 50, 75), Triple(0, 100, 100))
        for ((pull, push, squat) in athletes) {
            val result = model.strengthTest.result(pull, push, squat, skill = 0, Tier.PRO)
            val engine = Placement.start(pull, push, squat, starts)
            assertEquals(engine, result.start, "$pull/$push/$squat")
            assertEquals(engine, result.counted)
            assertEquals(Placement.better(pull, push, squat, starts), result.better)
            // The class it opens on, and the exam it takes for granted.
            val opens = model.strengthTest.programAt(result.start)!!
            assertEquals(result.start.level to result.start.day, opens.levelNumber to opens.number)
            assertEquals(Placement.opening(opens), result.opening)
            assertEquals(Placement.assumed(ordered, result.start, TrainingProgram::levelNumber, TrainingProgram::number,
                TrainingProgram::isExam)?.id, result.assumed?.id)
        }
        assertEquals(Placement.Start(1), model.strengthTest.result(0, 0, 0, 0, Tier.PRO).start)
        assertEquals(Placement.Start(2), model.strengthTest.result(10, 30, 0, 0, Tier.PRO).start)
        assertEquals(Placement.Start(3), model.strengthTest.result(20, 50, 75, 0, Tier.PRO).start)
        assertTrue(model.strengthTest.result(0, 0, 0, 0, Tier.PRO).fromScratch)
    }

    @Test
    fun theSkillsStepOffersThePassedLevelTestsHeldToTheTier() {
        val model = model()
        // Counts that pass every level test's pull-ups and push-ups: Levels 3 to 9.
        val tests = model.strengthTest.tests(30, 60)
        assertEquals((3..9).toList(), tests.map { it.levelNumber })
        assertTrue(tests.all { it.isExam && it.number == 100 })
        assertTrue(model.strengthTest.tests(0, 0).isEmpty())
        for ((tier, expected) in listOf(Tier.FREE to 3, Tier.PLUS to 6, Tier.PRO to 7)) {
            val result = model.strengthTest.result(30, 60, 80, skill = 6, tier = tier)
            assertEquals(Placement.Start(7), result.wanted)
            assertEquals(Placement.capped(Placement.Start(7), tier), result.start)
            assertEquals(Placement.Start(expected), result.start, "$tier")
            assertNull(result.better)
        }
        // A test the counts don't pass is dropped.
        val weaker = model.strengthTest.result(5, 10, 10, skill = 6, tier = Tier.PRO)
        assertEquals(0, weaker.skill)
        assertEquals(weaker.counted, weaker.start)
    }

    @Test
    fun aStartMarksEveryEarlierClassPlacedWithNoPointsAndSettlesTheTest() {
        val store = MemoryStore()
        val model = model(store)
        assertTrue(model.placementAsked())
        val start = model.strengthTest.result(10, 30, 75, 0, Tier.PRO).start
        model.placementForm.set(0, 10)
        model.startPlacement(start)

        val opens = model.strengthTest.programAt(start)!!
        val before = ordered.takeWhile { it.id != opens.id }
        assertEquals(before.map { it.id }.toSet(), model.progress.completedProgramIds())
        assertTrue(model.progress.sessionHistory().all { it.placed && it.manuallyCompleted && it.points == null })
        assertEquals(opens.id, model.today().next!!.id)
        assertEquals(0, model.points())
        assertEquals("true", store.get("streetgym_settings.placement_done"))
        assertFalse(model.placementAsked())
        // The form is cleared for a later Start over.
        assertEquals(listOf(-1, -1, -1), model.placementForm.counts.toList())
        // Placed classes are written as Android writes them.
        assertTrue(store.get("streetgym_progress.session_history")!!.contains("\"placed\":true"))
    }

    @Test
    fun skippingStartsAtLevelOneAndAnyProgressSettlesTheTest() {
        val store = MemoryStore()
        val model = model(store)
        model.startPlacement(StrengthResult.SCRATCH)
        assertTrue(model.progress.completedProgramIds().isEmpty())
        assertEquals(ordered[0].id, model.today().next!!.id)
        assertEquals("true", store.get("streetgym_settings.placement_done"))
        // An athlete from before the test existed: a class done, never asked, and settled on the phone.
        val older = MemoryStore()
        val veteran = model(older)
        veteran.completeDay(ordered[0].id)
        assertFalse(veteran.placementAsked())
        assertEquals("true", older.get("streetgym_settings.placement_done"))
        // A class in progress also counts.
        val busy = model()
        busy.start(ordered[0].id)
        assertFalse(busy.placementAsked())
    }

    @Test
    fun theTestByTaps() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        setContent { StreetGymApp(model, PaddingValues()) }

        onNodeWithText("FIRST, A QUICK TEST").assertExists()
        onNodeWithText("HOW STRONG ARE YOU TODAY?").assertExists()
        onNodeWithText("Warm up, then one set of each", substring = true).assertExists()
        // No brand card, day card or progress card while it is asked.
        onNodeWithText("START WORKOUT").assertDoesNotExist()
        onNodeWithText("Strict: chin over the bar", substring = true).assertExists()
        onNodeWithText("CALCULATE").assertIsNotEnabled()
        onNodeWithText("Enter all three, then tap Calculate.").assertExists()

        // Typed, and stepped with − and +.
        onNodeWithContentDescription("Strict pull-ups").performTextInput("9")
        onNodeWithContentDescription("One rep more, Strict pull-ups").performClick()
        onNodeWithContentDescription("Push-ups").performTextInput("31")
        onNodeWithContentDescription("One rep fewer, Push-ups").performClick()
        assertEquals(listOf(10, 30, -1), model.placementForm.counts.toList())
        onNodeWithText("CALCULATE").assertIsNotEnabled()
        onNodeWithContentDescription("Bodyweight squats").performTextInput("0")
        onNodeWithText("Enter all three, then tap Calculate.").assertDoesNotExist()
        onNodeWithText("CALCULATE").assertIsEnabled().performClick()

        // The start on tape, what it rests on, and the confirm.
        val result = model.placementResult()!!
        assertEquals(Placement.Start(2), result.start)
        val levelTwo = program.catalog.levels.first { it.number == 2 }.name.resolve()
        onNodeWithText("YOUR START").assertExists()
        onNodeWithText("Level 2 · $levelTwo", ignoreCase = true).assertExists()
        onNodeWithText("OPENS WITH").assertExists()
        onNodeWithText("ASSUMES YOU CAN DO").assertExists()
        onNodeWithText("CLOSER TO THE NEXT START").assertExists()
        val better = result.better!!
        onNodeWithText("${better.more} more pull-up${if (better.more == 1) "" else "s"} → ${where(better.start)}").assertExists()
        onNodeWithText("Only a guide. Skipped days earn no points or ranks.").assertExists()
        onNodeWithText("START AT LEVEL 2").assertExists()
        onNodeWithText("Start from scratch").assertExists()

        // A changed count hides the start until Calculate again.
        onNodeWithContentDescription("Bodyweight squats").performTextReplacement("75")
        onNodeWithText("YOUR START").assertDoesNotExist()
        onNodeWithText("CALCULATE").performClick()
        onNodeWithText("START AT LEVEL 2, DAY 21").performScrollTo().assertExists()

        // The skills step offers the level tests these counts pass, folded.
        val tests = model.strengthTest.tests(10, 30).map { it.levelNumber }
        assertEquals(tests.isNotEmpty(), onAllNodesWithText("ALREADY HOLD SKILLS?").fetchSemanticsNodes().isNotEmpty())
        tests.forEach { onNodeWithContentDescription("Level $it test").assertDoesNotExist() }
        onNodeWithText("START AT LEVEL 2, DAY 21").performClick()

        // Today, at that class; the classes before it placed.
        val opens = ordered.first { it.levelNumber == 2 && it.number == 21 }
        assertEquals(opens.id, model.today().next!!.id)
        onNodeWithText("START WORKOUT").performScrollTo().assertExists()
        onNodeWithText("FIRST, A QUICK TEST").assertDoesNotExist()
        assertEquals(ordered.indexOf(opens), model.progress.completedProgramIds().size)
    }

    @Test
    fun theSkillsStepAndAStartHeldToFree() = runComposeUiTest {
        val model = model(tier = Tier.FREE)
        setContent { StreetGymApp(model, PaddingValues()) }
        onNodeWithContentDescription("Strict pull-ups").performTextInput("30")
        onNodeWithContentDescription("Push-ups").performTextInput("60")
        // The keyboard's Done on the last counter runs Calculate.
        onNodeWithContentDescription("Bodyweight squats").performTextInput("80")
        onNodeWithContentDescription("Bodyweight squats").performImeAction()
        onNodeWithText("YOUR START").assertExists()

        // Folded under one line; a tap opens it, with every level test the counts pass.
        onNodeWithText("ALREADY HOLD SKILLS?").performScrollTo().performClick()
        onNodeWithText("Tap the hardest level test you'd pass today", substring = true).assertExists()
        for (level in 3..9) onNodeWithContentDescription("Level $level test").performScrollTo().assertExists()
        onNodeWithContentDescription("Level 6 test").performClick()
        assertEquals(6, model.placementForm.skill)
        val levelSeven = program.catalog.levels.first { it.number == 7 }.name.resolve()
        onNodeWithText("FROM YOUR SKILLS").performScrollTo().assertExists()
        onNodeWithText("Passing the Level 6 test suggests Level 7: $levelSeven. Level 7 needs Pro, so you start at Level 3, " +
            "the last one your plan opens.").assertExists()
        onNodeWithText("CLOSER TO THE NEXT START").assertDoesNotExist()
        onNodeWithText("START AT LEVEL 3").performScrollTo().assertExists()
        // A second tap clears the pick.
        onNodeWithContentDescription("Level 6 test").performScrollTo().performClick()
        assertEquals(0, model.placementForm.skill)
        onNodeWithText("FROM YOUR SKILLS").assertDoesNotExist()
    }

    @Test
    fun skipAndTheSharingSwitch() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        setContent { StreetGymApp(model, PaddingValues()) }
        // The sharing note closes the screen; its switch is on until switched off, under Android's key.
        assertTrue(model.shareProgress)
        onNodeWithContentDescription("Share my training progress").performScrollTo().assertIsOn().performClick()
        onNodeWithContentDescription("Share my training progress").assertIsOff()
        assertEquals("false", store.get("streetgym_settings.share_progress"))
        assertFalse(AppSettings(store).shareProgress)

        onNodeWithText("Skip and start at Level 1").performScrollTo().performClick()
        onNodeWithText("START WORKOUT").performScrollTo().assertExists()
        assertEquals(ordered[0].id, model.today().next!!.id)
        assertEquals("true", store.get("streetgym_settings.placement_done"))
    }

    @Test
    fun androidsBoldIsKeptAndDrawnBold() = runBlocking {
        val body = getString(Res.string.share_body)
        assertTrue(body.startsWith("<b>Share my progress.</b> Your training progress"), body)
        val bold = SpanStyle(fontWeight = FontWeight.Bold)
        val styled = boldMarkup(body, bold)
        assertTrue(styled.text.startsWith("Share my progress. Your training progress"))
        assertEquals(listOf(0 to "Share my progress.".length), styled.spanStyles.map { it.start to it.end })
        assertEquals(bold, styled.spanStyles.single().item)
        assertEquals("plain", boldMarkup("plain", bold).text)
        assertEquals("a <b>b", boldMarkup("a <b>b", bold).text)
    }

    @Test
    fun theLoadingScreenTheLoadErrorAndTryAgain() = runComposeUiTest {
        val first = CompletableDeferred<Program>()
        val ready = program
        var attempts = 0
        setContent {
            App(MemoryStore(), load = {
                attempts++
                if (attempts == 1) first.await() else ready
            })
        }
        onNodeWithText("Loading your training…").assertExists()
        assertEquals(1, onAllNodesWithContentDescription("Line drawing of a figure", substring = true).fetchSemanticsNodes().size)
        first.completeExceptionally(IllegalStateException("unreadable"))
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Training plan unavailable", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Your training could not be opened. Close the app and try again.").assertExists()
        onNodeWithContentDescription("Street Gym logo").assertExists()
        onNodeWithText("STREET GYM").assertExists()
        onNodeWithText("TRY AGAIN").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("FIRST, A QUICK TEST").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, attempts)
    }

    @Test
    fun todaysParkIsSpokenWithTheDaysMovementsAndReplays() = runComposeUiTest {
        val store = MemoryStore().apply { AppSettings(this).placementDone = true }
        val model = model(store)
        setContent { CompositionLocalProvider(LocalReduceMotion provides true) { StreetGymApp(model, PaddingValues()) } }
        // 18 Nov 2026: squats, a muscle-up and a front lever (Android's routine for that day).
        val park = onNodeWithContentDescription("Line drawing: a figure walks into the park, does squats and a muscle-up, then " +
            "briefly holds a front lever while friends cheer. The three exercises repeat.")
        park.assertExists()
        assertEquals("Replay", park.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)
        park.performClick()
        park.assertExists()
        // During the test the brand card, the day card and the progress card are back.
        onNodeWithText("DAILY CALISTHENICS").assertExists()
        onNodeWithText("START WORKOUT").performScrollTo().assertExists()
    }

    @Test
    fun startOverAsksTheTestAgain() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        model.startPlacement(model.strengthTest.result(10, 30, 0, 0, Tier.PRO).start)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }
        model.reset()
        assertTrue(model.placementAsked())
        assertEquals("false", store.get("streetgym_settings.placement_done"))
        onNodeWithContentDescription("Today").performClick()
        onNodeWithText("FIRST, A QUICK TEST").assertExists()
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
