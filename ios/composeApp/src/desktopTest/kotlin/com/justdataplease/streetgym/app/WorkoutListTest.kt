package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.Training
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.section_check_description
import com.justdataplease.streetgym.app.resources.set_complete_description
import com.justdataplease.streetgym.app.resources.warm_up
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.ui.WorkoutFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The list view (Android: HomeActivity.renderActive) driven by taps: a set's tick and the rest it starts, a section's
 * tick, the title bar's switch between Focus and List with both in step, finishing, Complete day and End session from
 * the list, and a completed class repeated from its page.
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutListTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun model(store: MemoryStore = MemoryStore()) =
        AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store))

    private fun text(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) = runBlocking { getString(resource, *args) }

    /** The check box's words for the set at [index] of [flow] ("Mark set 1 of Low-Impact Jack complete"). */
    private fun setBox(flow: WorkoutFlow, index: Int) = text(Res.string.set_complete_description, flow[index].setNumber, flow[index].exercise.name.resolve())

    /** The row of the set at [index]; sets with the same words are told apart by their order in the class. */
    private fun SemanticsNodeInteractionsProvider.setNode(flow: WorkoutFlow, index: Int): SemanticsNodeInteraction {
        val words = setBox(flow, index)
        return onAllNodesWithContentDescription(words)[flow.steps.indices.count { it < index && setBox(flow, it) == words }]
    }

    /** A set of [flow] from [from] on whose words are its own, with a rest after it, that ticks without asking for a number. */
    private fun restedSet(model: AppModel, flow: WorkoutFlow, from: Int = 0): Int = flow.steps.indices.first { index ->
        val step = flow[index]
        index >= from &&
        (step.set.breakSeconds ?: 0) > 0 && flow.next(index) != null && (!step.credited || model.training.measure(step).planned != null) &&
            flow.steps.count { it.setNumber == step.setNumber && it.exercise.name.resolve() == step.exercise.name.resolve() } == 1
    }

    @Test
    fun aTickInTheListStartsTheRestAndASectionTicksAllItsSets() = runComposeUiTest {
        val model = model()
        val first = model.today().next!!
        model.workoutView = WorkoutView.LIST
        model.start(first.id)
        val flow = Training.flow(first)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }

        onNodeWithText("0 of ${flow.size} sets").assertExists()
        onNodeWithText("Ready for the next set").assertExists()
        // The focus view's title bar icon offers the other view, and the list keeps the tab bar.
        onNodeWithContentDescription("Switch to focus view").assertExists()
        onNodeWithText("TODAY").assertExists()

        // A set's tick saves it at once and starts its rest.
        val rested = restedSet(model, flow)
        setNode(flow, rested).performScrollTo().assertIsOff().performClick()
        setNode(flow, rested).assertIsOn()
        assertEquals(setOf(flow[rested].key), model.training.active()!!.completedSetIds)
        assertEquals(time, model.training.active()!!.tickedAt[flow[rested].key])
        val pause = flow[rested].set.breakSeconds!!
        onNodeWithText("Rest • $pause sec").assertExists()
        onNodeWithText("1 of ${flow.size} sets").assertExists()
        // Half of it later, the line counts down; at its end the next set is ready again.
        time += pause / 2 * 1_000L
        model.training.tick()
        onNodeWithText("Rest • ${pause - pause / 2} sec").assertExists()
        time += pause * 1_000L
        model.training.tick()
        onNodeWithText("Ready for the next set").assertExists()

        // The warm-up's own tick: every set of it, then none.
        val warmUp = flow.keys(Training.WARM_UP)
        val section = text(Res.string.section_check_description, text(Res.string.warm_up))
        onNodeWithContentDescription(section).performScrollTo().assertIsOff().performClick()
        assertTrue(model.training.active()!!.completedSetIds.containsAll(warmUp))
        onNodeWithContentDescription(section).assertIsOn()
        onNodeWithContentDescription(section).performClick()
        assertTrue(model.training.active()!!.completedSetIds.none { it in warmUp })
        onNodeWithContentDescription(section).assertIsOff()
    }

    @Test
    fun focusAndListShowTheSameClassAndTheSwitchIsKept() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        val first = model.today().next!!
        model.start(first.id)
        val flow = Training.flow(first)
        // Two sets done in Focus.
        for (index in 0..1) {
            time += 40_000L
            model.training.done(index)
            model.training.skipRest()
        }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }
        onNodeWithText("Set 3 of ${flow.size}").assertExists()

        // The list shows them ticked, and the choice is saved as Android saves it.
        onNodeWithContentDescription("Switch to list view").performClick()
        assertEquals(WorkoutView.LIST, model.workoutView)
        assertEquals("list", store.get("streetgym_settings.workout_view"))
        onNodeWithText("2 of ${flow.size} sets").assertExists()
        setNode(flow, 0).performScrollTo().assertIsOn()
        setNode(flow, 1).performScrollTo().assertIsOn()

        // A set ticked in the list moves Focus on, and the rest it starts is the focus view's rest.
        val rested = restedSet(model, flow, from = 2)
        // Every set before it ticked in the list too, so Focus lands on the rest before the set after it.
        for (index in 2 until rested) model.training.tick(flow[index].key, true)
        model.training.skipRest()
        setNode(flow, rested).performScrollTo().performClick()
        onNodeWithContentDescription("Switch to focus view").performClick()
        assertEquals(WorkoutView.FOCUS, model.workoutView)
        val page = model.training.state()!!.page
        assertTrue(page is FocusPage.Rest && page.index == rested + 1, "focus rests before the next set: $page")
        onNodeWithText("REST").assertExists()

        // A new app on the same phone opens the class in the view last picked.
        model.workoutView = WorkoutView.LIST
        assertEquals(WorkoutView.LIST, model(store).workoutView)
    }

    @Test
    fun finishingFromTheListSavesTheClass() = runComposeUiTest {
        val model = model()
        val first = model.today().next!!
        model.workoutView = WorkoutView.LIST
        model.start(first.id)
        val flow = Training.flow(first)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }

        // Every section ticked by its own box, a few minutes apart.
        for (section in Training.sections(first).filter { it.second.isNotEmpty() }) {
            time += 4 * 60_000L
            model.training.tickSection(section.first, true)
        }
        onNodeWithText("${flow.size} of ${flow.size} sets").assertExists()
        // A whole class ticked: no rest, straight to the effort question.
        onNodeWithText("Ready for the next set").assertExists()
        onNodeWithText("FINISH WORKOUT").performScrollTo().performClick()
        onNodeWithText("How hard did that feel?").assertExists()
        onNodeWithText("FINISH").performClick()

        onNodeWithText("Workout completed", substring = true).assertExists()
        assertNull(model.training.active())
        assertTrue(model.isCompleted(first.id))
        assertEquals(first.id, model.progress.sessionHistory().single().programId)
    }

    @Test
    fun completeDayFromTheListAsksFirstAndEndSessionDropsTheClass() = runComposeUiTest {
        val model = model()
        val first = model.today().next!!
        model.workoutView = WorkoutView.LIST
        model.start(first.id)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }

        // Complete day asks, and Cancel keeps the class.
        onNodeWithText("COMPLETE DAY").performClick()
        onNodeWithText("Complete this day?").assertExists()
        onNodeWithText("CANCEL").performClick()
        assertNotNull(model.training.active())

        onNodeWithText("COMPLETE DAY").performClick()
        onNodeWithText("COMPLETE").performClick()
        // Today, the next class; the first one marked done without training.
        onNodeWithText("START WORKOUT").assertExists()
        assertNull(model.training.active())
        assertTrue(model.isCompleted(first.id))
        assertTrue(model.progress.sessionHistory().single().manuallyCompleted)
    }

    @Test
    fun aCompletedClassIsRepeatedFromItsPage() = runComposeUiTest {
        val model = model()
        val first = model.today().next!!
        trainAndFinish(model, first)
        val next = model.today().next!!
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(first.id)) }

        // At the top of the page and at its end.
        assertEquals(2, onAllNodesWithText("REPEAT THIS DAY").fetchSemanticsNodes().size)
        onAllNodesWithText("REPEAT THIS DAY")[0].performClick()
        onNodeWithText("Set 1 of ${Training.flow(first).size}").assertExists()
        val active = model.training.active()!!
        assertTrue(active.repeat)
        assertEquals(first.id, active.programId)
        // Today resumes the repeat with its note; the program's place stays.
        assertTrue(model.today().repeating)
        assertTrue(model.today().resume)

        time += 20 * 60_000L
        val session = model.training.finish(6)!!
        assertTrue(session.session.repeat)
        assertEquals(setOf(first.id), model.progress.completedProgramIds())
        assertEquals(next.id, model.today().next!!.id)
        assertFalse(model.today().repeating)
        assertEquals(2, model.progress.sessionHistory().size)
    }

    @Test
    fun theClassPageSetsTheWorkoutView() = runComposeUiTest {
        val model = model()
        val first = model.today().next!!
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(first.id)) }

        onNodeWithText("List").performScrollTo().performClick()
        assertEquals(WorkoutView.LIST, model.workoutView)
        onNodeWithText("START WORKOUT").performScrollTo().performClick()
        onNodeWithText("Ready for the next set").assertExists()
        onNodeWithText("0 of ${Training.flow(first).size} sets").assertExists()
    }

    @Test
    fun theListsLastTickEndsTheRestAndUntickingKeepsIt() {
        val model = model()
        val first = model.today().next!!
        model.start(first.id)
        val flow = Training.flow(first)
        val rested = restedSet(model, flow)
        model.training.tick(flow[rested].key, true)
        val running = model.training.state()!!.restEndsAt
        assertEquals(time + flow[rested].set.breakSeconds!! * 1_000L, running)
        // Unticking leaves the rest as it is.
        model.training.tick(flow[rested].key, false)
        assertEquals(running, model.training.state()!!.restEndsAt)
        // The whole class ticked from the sections: the rest ends.
        Training.sections(first).forEach { (section, _) -> model.training.tickSection(section, true) }
        assertNull(model.training.state()!!.restEndsAt)
        assertEquals(FocusPage.Summary, model.training.state()!!.page)
    }

    private fun trainAndFinish(model: AppModel, program: TrainingProgram) {
        model.start(program.id)
        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            time += 60_000L
            model.training.done(index)
            model.training.skipRest()
        }
        model.training.finish(7)
        time += 60 * 60_000L
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
