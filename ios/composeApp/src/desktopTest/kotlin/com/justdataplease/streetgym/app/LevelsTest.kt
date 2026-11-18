package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.LevelState
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.TodayLabel
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.empty_description
import com.justdataplease.streetgym.app.resources.placement_level_one_hint
import com.justdataplease.streetgym.app.resources.replay_today
import com.justdataplease.streetgym.app.ui.DAILY_PLAN_LOCKED
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.ExamResult
import com.justdataplease.streetgym.data.progress.NextClass
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Training › Levels and a level's classes (Android: renderLevels, levelCard, replayCard, renderWorkouts and
 * ProgramListAdapter) over the real program, by taps: the tabs, the ten levels and their states, a level's rows and
 * marks, Complete level, going back to a level and returning, exam rows, and locked levels on a lower tier.
 */
@OptIn(ExperimentalTestApi::class)
class LevelsTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun model(store: MemoryStore = MemoryStore(), tier: Tier = Tier.PRO) =
        AppModel(program, StoredProgress(store), tier = tier, now = { time }, zone = TimeZone.UTC, settings = AppSettings(store))

    /** The first [count] classes marked done. */
    private fun done(model: AppModel, count: Int) = model.progress.completePrograms(ordered.take(count).map { it.id }, at = time - 86_400_000L)

    private fun text(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) = runBlocking { getString(resource, *args) }

    /** The spoken line of a node: its content description. */
    private fun SemanticsNodeInteraction.spoken(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()

    /** A class's row in a level's list, scrolled into view. */
    private fun SemanticsNodeInteractionsProvider.row(program: TrainingProgram): SemanticsNodeInteraction {
        val start = "Workout ${program.number}, ${program.name.resolve()},"
        onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(start, substring = true))
        return onNodeWithContentDescription(start, substring = true)
    }

    @Test
    fun trainingShowsItsTabsAndTheTenLevels() = runComposeUiTest {
        val model = model()
        done(model, 12)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Training) }

        onNodeWithText("Levels").assertIsSelected()
        listOf("My workouts", "Library", "Parks").forEach { onNodeWithText(it).assertExists() }
        onNodeWithText(text(Res.string.empty_description)).assertExists()
        // Level 1 is under way and current; the nine others lie ahead.
        val levels = model.levels()
        assertEquals(10, levels.cards.size)
        assertEquals(listOf(LevelState.CURRENT) + List(9) { LevelState.UPCOMING }, levels.cards.map { it.state })
        val first = program.catalog.levels.first()
        onNodeWithContentDescription("${first.name.resolve()}, 12 of 100 complete").assertExists()
        assertEquals(9, onAllNodesWithText("0 of 100 complete").fetchSemanticsNodes().size)

        // A level opens its classes, titled as Android titles them.
        onNodeWithContentDescription("${program.catalog.levels[1].name.resolve()}, 0 of 100 complete").performScrollTo().performClick()
        onNodeWithText("LEVEL 2 WORKOUTS").assertExists()

        // The tab picked stays while the app runs.
        onNodeWithContentDescription("Training").performClick()
        onNodeWithText("Library").performClick()
        onNodeWithText("Not on iPhone yet.").assertExists()
        onNodeWithText("My workouts").performClick()
        onNodeWithText("Save any day with the heart on its overview", substring = true).assertExists()
        onNodeWithContentDescription("Today").performClick()
        onNodeWithContentDescription("Training").performClick()
        onNodeWithText("My workouts").assertIsSelected()
    }

    @Test
    fun aLevelListsItsClassesWithTheirMarks() = runComposeUiTest {
        val model = model()
        done(model, 12)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Level(1)) }

        onNodeWithText("LEVEL 1 WORKOUTS").assertExists()
        onNodeWithText("12 of 100 complete").assertExists()
        onNodeWithText(text(Res.string.placement_level_one_hint)).assertExists()
        onNodeWithText("COMPLETE LEVEL").assertExists()
        // Done, next, later: the row's spoken line ends with its mark, and the meta shows only once reached.
        assertTrue(row(ordered[0]).spoken().endsWith(", Completed"))
        val next = row(ordered[12]).spoken()
        assertTrue(next.endsWith(", Next"), next)
        // "Moderate for you", with Android's non-breaking spaces.
        assertTrue(" for you" in next, next)
        val later = row(ordered[13]).spoken()
        assertEquals("Workout 14, ${ordered[13].name.resolve()}, Later", later)

        // A class not reached shows only its name; the next one starts; a done one can be repeated.
        row(ordered[13]).performClick()
        onNodeWithText(DAILY_PLAN_LOCKED).assertExists()
        onNodeWithText("ASSIGNED LATER IN THE SEQUENCE").assertExists()
    }

    @Test
    fun rowsOpenTheirClassPage() = runComposeUiTest {
        val model = model()
        done(model, 12)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Level(1)) }
        row(ordered[12]).performClick()
        onNodeWithText("START WORKOUT").performScrollTo().assertExists()
        onNodeWithContentDescription("Back").performClick()
        row(ordered[0]).performClick()
        assertEquals(2, onAllNodesWithText("REPEAT THIS DAY").fetchSemanticsNodes().size)
    }

    @Test
    fun examRowsSayHowTheLastAttemptWent() {
        val model = model()
        val exams = ordered.filter { it.isExam && it.levelNumber == 1 }.take(2)
        assertEquals(2, exams.size)
        model.progress.recordExam(ExamResult(exams[0].id, time, reached = listOf("a"), missed = emptyList()))
        model.progress.recordExam(ExamResult(exams[1].id, time, reached = emptyList(), missed = listOf("a")))
        val rows = model.levelPage(1)!!.rows.associateBy { it.program.id }
        assertEquals(true, rows.getValue(exams[0].id).exam?.passed)
        assertEquals(false, rows.getValue(exams[1].id).exam?.passed)
        assertNull(rows.values.first { !it.program.isExam }.exam)
        assertTrue(rows.values.filter { it.program.isExam }.all { it.program.dayType!!.startsWith("exam") })
    }

    @Test
    fun completeLevelMarksEveryClassAndReturnsToTraining() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        done(model, 12)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Level(1)) }

        onNodeWithText("COMPLETE LEVEL").performClick()
        onNodeWithText("Complete this level?").assertExists()
        onNodeWithText("Mark all 100 days in ${program.catalog.levels[0].name.resolve()} complete.", substring = true).assertExists()
        onNodeWithText("COMPLETE").performClick()

        onNodeWithText(text(Res.string.empty_description)).assertExists()
        onNodeWithContentDescription("${program.catalog.levels[0].name.resolve()}, 100 of 100 complete").assertExists()
        assertEquals(listOf(LevelState.DONE, LevelState.CURRENT), model.levels().cards.take(2).map { it.state })
        // Marked done without training: 88 new days in the history, none of them worth anything.
        val history = model.progress.sessionHistory()
        assertEquals(100, history.size)
        assertTrue(history.all { it.manuallyCompleted })
        assertEquals(ordered[100].id, model.today().next!!.id)
    }

    @Test
    fun goingBackToALevelReplaysItAndTheReplayCardReturns() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        done(model, 105)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Level(1)) }

        onNodeWithText("COMPLETE LEVEL").assertDoesNotExist()
        onNodeWithText("GO BACK TO THIS LEVEL").performClick()
        onNodeWithText("Go back to Level 1?").assertExists()
        onNodeWithText("GO BACK").performClick()

        // Today starts again at Level 1's first class and says so; the replay is kept under Android's names.
        onNodeWithText(text(Res.string.replay_today, 1)).assertExists()
        assertEquals(ordered[0].id, model.today().next!!.id)
        assertEquals(TodayLabel.Replay(1), model.today().label)
        assertEquals("1", store.get("streetgym_progress.replay_level"))
        assertNull(store.get("streetgym_progress.replay_from_program_id"))

        // Training shows the replay and where the athlete stopped; returning ends it.
        onNodeWithContentDescription("Training").performClick()
        onNodeWithText("REPEATING FROM LEVEL 1").assertExists()
        onNodeWithText("You stopped at Level 2 · ${ordered[105].name.resolve()}", substring = true).assertExists()
        onNodeWithText("RETURN TO MY FURTHEST DAY").performClick()
        assertNull(store.get("streetgym_progress.replay_level"))
        assertEquals(ordered[105].id, model.today().next!!.id)
        onNodeWithText("REPEATING FROM LEVEL 1").assertDoesNotExist()
    }

    @Test
    fun lockedLevelsWearTheirTierAndOpenTheOffer() = runComposeUiTest {
        val model = model(tier = Tier.FREE)
        done(model, 300)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Today) }

        // Today's class is in Level 4, which Free doesn't open: Unlock, and the offer in Android's words.
        assertTrue(model.today().locked)
        onNodeWithText("START WORKOUT").assertDoesNotExist()
        onNodeWithContentDescription("Unlock Plus").performScrollTo().performClick()
        onNodeWithText("Level 4 is part of Plus").assertExists()
        onNodeWithText("CANCEL").performClick()

        // Levels: the tier's tag beside the name of every locked level.
        onNodeWithContentDescription("Training").performClick()
        val levels = program.catalog.levels
        onNodeWithContentDescription("${levels[3].name.resolve()}, 0 of 100 complete, Plus").performScrollTo().assertExists()
        onNodeWithContentDescription("${levels[6].name.resolve()}, 0 of 100 complete, Pro").performScrollTo().assertExists()
        onNodeWithContentDescription("${levels[2].name.resolve()}, 100 of 100 complete").performScrollTo().assertExists()

        // A locked level's page: its tag opens the offer, and so does a class not done; no Complete level.
        onNodeWithContentDescription("${levels[6].name.resolve()}, 0 of 100 complete, Pro").performClick()
        onNodeWithText("COMPLETE LEVEL").assertDoesNotExist()
        onNodeWithText("GO BACK TO THIS LEVEL").assertDoesNotExist()
        onNodeWithContentDescription("Pro").performClick()
        onNodeWithText("Level 7 is part of Pro").assertExists()
        onNodeWithText("CANCEL").performClick()
        val locked = ordered.first { it.levelNumber == 7 }
        assertTrue(row(locked).spoken().endsWith(", Pro"))
        row(locked).performClick()
        onNodeWithText("Level 7 is part of Pro").assertExists()
        assertFalse(model.canStart(locked.id))
    }

    @Test
    fun aLockedClassPageShowsUnlockOnly() = runComposeUiTest {
        val model = model(tier = Tier.FREE)
        val locked = ordered.first { it.levelNumber == 5 }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(locked.id)) }
        onNodeWithText("UNLOCK").assertExists()
        onNodeWithText("START WORKOUT").assertDoesNotExist()
        onNodeWithText("WARM-UP").assertDoesNotExist()
        onNodeWithText("UNLOCK").performClick()
        onNodeWithText("Level 5 is part of Plus").assertExists()
    }

    @Test
    fun aDoneClassOfALevelTheTierNoLongerOpensIsShownButNotRepeated() = runComposeUiTest {
        val model = model(tier = Tier.FREE)
        val done = ordered.first { it.levelNumber == 4 }
        model.progress.completePrograms(listOf(done.id), at = time)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(done.id)) }
        onNodeWithText("WORKOUT COMPLETED").performScrollTo().assertExists()
        onNodeWithText("You completed this workout in the guided sequence.").assertExists()
        onNodeWithText("REPEAT THIS DAY").assertDoesNotExist()
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
