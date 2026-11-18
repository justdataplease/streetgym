package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.LeftOut
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.equipment_off_message
import com.justdataplease.streetgym.app.resources.switch_off_class
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentSwitches
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.skills.SkillProgressions
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.data.skills.switchedOff
import com.justdataplease.streetgym.ui.WorkoutFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The skill and equipment switches of Settings acting where Android's act (HomeActivity.leftOutBy, askSwitchedOff,
 * renderActive, renderFocus, passedIds; SkillSwitchesView, EquipmentView): kept under Android's keys, a switched-off
 * class passed and its row SWITCHED OFF, greyed exercises on the class page, the focus view leaving their sets out and
 * offering them anyway, the list asking before a tick, the exam and the credits, and the Settings rows by taps.
 */
@OptIn(ExperimentalTestApi::class)
class SwitchesTest {
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun text(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) = runBlocking { getString(resource, *args) }

    private fun model(store: MemoryStore = MemoryStore()) =
        AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store))

    /** The athlete at [target]: every class before it done. */
    private fun at(model: AppModel, target: TrainingProgram) {
        model.progress.completePrograms(ordered.takeWhile { it.id != target.id }.map { it.id }, at = time - 86_400_000L)
    }

    private val handstandOff = SkillSwitches().with("handstand", 0)

    /** Every skill switched off as far as it goes: some classes are left with nothing to train. */
    private val allOff: SkillSwitches by lazy {
        book.families.map { it.spec }.fold(SkillSwitches()) { switches, spec ->
            if (SkillProgressions.LINES[spec.id].isNullOrEmpty()) switches else switches.with(spec.id, SkillProgressions.firstSwitchable(spec))
        }
    }

    /** A class with handstand work in its practice or strength work beside other work, so it still runs. */
    private val partial: TrainingProgram by lazy {
        ordered.first { program ->
            program.levelNumber <= 3 && !program.switchedOff(handstandOff) &&
                (program.practice + program.exercises).any { it.movementId?.let(handstandOff::isOff) == true && !it.readinessCheck }
        }
    }

    @Test
    fun switchesAreKeptUnderAndroidsKeysAndPassClasses() {
        val store = MemoryStore()
        val model = model(store)
        model.skillSwitches = handstandOff.with("planche", 2)
        model.equipment = EquipmentSwitches(setOf(Equipment.WALL, Equipment.PULLUP_BAR))
        assertEquals("handstand:0,planche:2", store.get("streetgym_settings.skill_switches"))
        assertEquals("pullup_bar,wall", store.get("streetgym_settings.equipment_off"))
        // A restart reads them back.
        val again = model(store)
        assertEquals(model.skillSwitches, again.skillSwitches)
        assertEquals(model.equipment, again.equipment)

        // A class with nothing left to train counts as passed: Today steps over it and its row says so.
        model.equipment = EquipmentSwitches()
        model.skillSwitches = allOff
        val passed = ordered.drop(1).first { it.switchedOff(allOff) }
        val before = ordered[ordered.indexOf(passed) - 1]
        at(model, before)
        assertNotEquals(passed.id, model.today().next?.id)
        assertEquals(before.id, model.today().next?.id)
        assertTrue(model.levelPage(passed.levelNumber)!!.rows.first { it.program.id == passed.id }.off)
        assertTrue(model.switchedOff(passed))
        // Switched back on, it is a class like any other.
        model.skillSwitches = SkillSwitches()
        assertFalse(model.levelPage(passed.levelNumber)!!.rows.first { it.program.id == passed.id }.off)
    }

    @Test
    fun fromARungTheEasierStepsStay() {
        val model = model()
        model.skillSwitches = SkillSwitches().with("planche", 2)
        fun off(movement: String) = model.skillSwitches.isOff(movement)
        // Rungs 0 and 1 and the steps toward them stay; rung 2 and every harder step go.
        assertFalse(off("planche_lean"))
        assertFalse(off("tuck_planche"))
        assertTrue(off("advanced_tuck_planche"))
        assertTrue(off("tuck_planche_pushup"))
        assertTrue(off("full_planche"))
        val exercise = ordered.flatMap { it.practice + it.exercises }.first { it.movementId == "advanced_tuck_planche" }
        val program = ordered.first { exercise in it.practice + it.exercises }
        val section = if (exercise in program.practice) "practice" else "main"
        val index = (if (section == "practice") program.practice else program.exercises).indexOf(exercise)
        assertEquals(LeftOut.Skill("Planche"), model.leftOutBy(program, section, exercise, index))
    }

    @Test
    fun theClassPageGreysASwitchedOffExercise() = runComposeUiTest {
        val model = model()
        model.skillSwitches = handstandOff
        at(model, partial)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(partial.id)) }
        onAllNodesWithText("SWITCHED OFF · HANDSTAND").onFirst().performScrollTo().assertExists()
        // A class left with nothing to train says it counts as passed.
        onNodeWithText(text(Res.string.switch_off_class)).assertDoesNotExist()
    }

    @Test
    fun aPassedClassPageSaysSo() = runComposeUiTest {
        val model = model()
        model.skillSwitches = allOff
        // Android shows a class not assigned as "locked"; its page with the note opens in a replay or to inspect.
        model.inspectAllDays = true
        val passed = ordered.drop(1).first { it.switchedOff(allOff) }
        at(model, ordered[ordered.indexOf(passed) - 1])
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Level(passed.levelNumber)) }
        val row = "Workout ${passed.number}, ${passed.name.resolve()},"
        onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasContentDescription(row, substring = true))
        onNodeWithContentDescription(row, substring = true).performClick()
        onNodeWithText(text(Res.string.switch_off_class)).assertExists()
    }

    @Test
    fun theFocusViewLeavesTheSetsOutAndOffersThemAnyway() = runComposeUiTest {
        val model = model()
        model.skillSwitches = handstandOff
        at(model, partial)
        model.start(partial.id)
        val state = model.training.state()!!
        assertTrue(state.leftOut.isNotEmpty())
        assertEquals(state.all.size - state.leftOut.size, state.flow.size)
        assertTrue(state.flow.keys.none { it in state.leftOut })
        // Every set the athlete trains, then the summary.
        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            time += 60_000L
            model.training.done(index)
            model.training.skipRest()
        }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(partial.id)) }
        val out = state.leftOut.size
        onNodeWithText(if (out == 1) "1 set left out: switched off in Settings" else "$out sets left out: switched off in Settings").assertExists()
        onNodeWithText("CLASS COMPLETE").assertExists()
        onNodeWithText("DO THEM ANYWAY").performClick()
        onNodeWithText("Handstand is switched off").assertExists()
        onNodeWithText("You've switched off the Handstand skill", substring = true).assertExists()
        onNodeWithText("CONTINUE").performClick()
        // Back in this workout: the first of them is the set shown, and nothing is left out any more.
        val after = model.training.state()!!
        assertTrue(after.leftOut.isEmpty())
        val page = after.page as FocusPage.Set
        assertTrue(after.flow[page.index].key in state.leftOut)
    }

    @Test
    fun theListAsksBeforeATickAndCountsTheSetOnce() = runComposeUiTest {
        val model = model()
        model.skillSwitches = handstandOff
        at(model, partial)
        model.workoutView = WorkoutView.LIST
        model.start(partial.id)
        val state = model.training.state()!!
        val step = state.all.steps.first { it.key in state.leftOut }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(partial.id)) }

        // The count leaves the switched-off sets out.
        onNodeWithText("0 of ${state.flow.size} sets").assertExists()
        val row = "Mark set ${step.setNumber} of ${step.exercise.name.resolve()} complete"
        onAllNodesWithText("SWITCHED OFF · HANDSTAND").onFirst().performScrollTo()
        onNodeWithContentDescription(row).performScrollTo().assertIsOff().performClick()
        onNodeWithText("Handstand is switched off").assertExists()
        onNodeWithText("CANCEL").performClick()
        assertFalse(step.key in model.training.active()!!.completedSetIds)
        onNodeWithContentDescription(row).performClick()
        onNodeWithText("CONTINUE").performClick()
        assertTrue(step.key in model.training.active()!!.completedSetIds)
        onNodeWithContentDescription(row).assertIsOn()
        // Done anyway, its sets count in this workout.
        val all = state.all.steps.count { it.exerciseKey == step.exerciseKey }
        onNodeWithText("1 of ${state.flow.size + all} sets").performScrollTo().assertExists()
    }

    @Test
    fun setsLeftOutEarnNothingUntilDoneAnyway() {
        val model = model()
        model.skillSwitches = handstandOff
        at(model, partial)
        model.start(partial.id)
        val state = model.training.state()!!
        val movement = state.all.steps.first { it.key in state.leftOut }.exercise.movementId!!
        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            time += 60_000L
            model.training.done(index)
            model.training.skipRest()
        }
        val finished = model.training.finish(6)!!
        assertNull(finished.session.credits?.get(movement))
        assertTrue(finished.points > 0)

        // The same class repeated with the handstand done anyway: its sets credit the skill.
        model.start(partial.id, repeat = true)
        val again = model.training.state()!!
        model.allowAnyway(partial.id, again.all.steps.filter { it.key in again.leftOut }.map { it.exerciseKey })
        assertTrue(model.training.state()!!.leftOut.isEmpty())
        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            time += 60_000L
            model.training.done(index)
            model.training.skipRest()
        }
        val repeat = model.training.finish(6)!!
        assertTrue((repeat.session.credits?.get(movement) ?: 0) > 0, repeat.session.credits.toString())
        assertTrue(repeat.points > finished.points)
        // Starting the class afresh forgets the choice, as Android's offAllowed.
        val flow = WorkoutFlow.of(partial.id, com.justdataplease.streetgym.app.data.Training.sections(partial))
        assertTrue(model.leftOutKeys(partial, flow).isEmpty())
        model.start(partial.id, repeat = true)
        assertTrue(model.leftOutKeys(partial, flow).isNotEmpty())
    }

    @Test
    fun missingEquipmentGreysItsExercisesAndAsksWithItsOwnWords() = runComposeUiTest {
        val model = model()
        model.equipment = EquipmentSwitches(setOf(Equipment.PULLUP_BAR))
        val target = ordered.first { program ->
            program.levelNumber <= 2 && !program.switchedOff(SkillSwitches(), model.equipment) &&
                (program.practice + program.exercises).any { model.equipment.isOff(it) && !it.readinessCheck }
        }
        at(model, target)
        model.workoutView = WorkoutView.LIST
        model.start(target.id)
        val state = model.training.state()!!
        val step = state.all.steps.first { it.key in state.leftOut }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(target.id)) }
        onAllNodesWithText("SWITCHED OFF · PULL-UP BAR").onFirst().performScrollTo().assertExists()
        onNodeWithContentDescription("Mark set ${step.setNumber} of ${step.exercise.name.resolve()} complete").performScrollTo().performClick()
        onNodeWithText("Pull-up bar is switched off").assertExists()
        onNodeWithText(text(Res.string.equipment_off_message)).assertExists()
    }

    @Test
    fun settingsSwitchSkillsAndEquipmentByTaps() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }

        // The whole handstand off with its switch; the row says so.
        onNodeWithContentDescription("Handstand").performScrollTo().assertIsOn().performClick()
        assertEquals("handstand:0", store.get("streetgym_settings.skill_switches"))
        onNodeWithContentDescription("Handstand").assertIsOff()
        // The pull-up's basics stay when it is switched off.
        onNodeWithContentDescription("Pull-up").performScrollTo().performClick()
        val pullup = book.families.first { it.spec.id == "pullup" }
        onNodeWithText("Switched off · ${pullup.skills[1].name} stays").assertExists()

        // A tap on the planche's row opens its steps: off from one step switches off every harder one.
        val planche = book.families.first { it.spec.id == "planche" }
        onNodeWithText("PLANCHE").performScrollTo().performClick()
        onNodeWithText("Switch off a step and every harder one goes too.", substring = true).assertExists()
        onNodeWithContentDescription(planche.skills[2].name).performClick()
        assertEquals("handstand:0,planche:2,pullup:2", store.get("streetgym_settings.skill_switches"))
        onNodeWithContentDescription(planche.skills[3].name).assertIsOff()
        // Switching a harder one on brings the easier back.
        onNodeWithContentDescription(planche.skills[3].name).performClick()
        assertEquals("handstand:0,planche:4,pullup:2", store.get("streetgym_settings.skill_switches"))
        onNodeWithContentDescription(planche.skills[2].name).assertIsOn()
        onNodeWithText("DONE").performClick()
        onNodeWithText("Up to ${planche.skills[3].name}").assertExists()

        // The pull-up's steps: its basics are always on.
        onNodeWithText("PULL-UP").performScrollTo().performClick()
        assertEquals(2, onAllNodesWithText("Always on: a basic").fetchSemanticsNodes().size)
        onNodeWithText("DONE").performClick()

        // Equipment: a tap anywhere on the row flips it.
        onNodeWithContentDescription("Pull-up bar").performScrollTo().assertIsOn().performClick()
        assertEquals("pullup_bar", store.get("streetgym_settings.equipment_off"))
        onNodeWithText("Switched off: classes leave these out").assertExists()
        assertTrue(model.equipment.isOff(ordered.flatMap { it.exercises }.first { it.movementId == "pullup" }))
        onNodeWithContentDescription("Pull-up bar").performClick()
        assertNull(store.get("streetgym_settings.equipment_off"))
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val book by lazy { program.skills!! }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
