package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.ReadyFilter
import com.justdataplease.streetgym.app.data.ReadyLibrary
import com.justdataplease.streetgym.app.data.Start
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.TRAINING_LIBRARY
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.effort.Effort
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentSwitches
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.skills.SkillProgressions
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Training › Library (Android: renderReadyWorkouts, libraryCard, readyLocked, renderOverview and canStart for ready
 * workouts) over the app's own workouts.json: the groups, counts and focus chips against the file, every filter, the
 * free workouts and the Pro offer below Pro, the page, and a ready workout trained to its end without moving the
 * program on.
 */
@OptIn(ExperimentalTestApi::class)
class ReadyLibraryTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun model(tier: Tier = Tier.PRO, store: MemoryStore = MemoryStore()): AppModel {
        AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), tier = tier, now = { time }, zone = TimeZone.UTC, settings = AppSettings(store),
            library = LibraryStore(store))
    }

    /** workouts.json as written, without the app's parser: id, group, grade, focus tags and whether it is free. */
    private val file: List<JSONObject> by lazy {
        val raw = JSONObject(runBlocking { com.justdataplease.streetgym.app.resources.Res.readBytes("files/workouts.json") }.decodeToString())
            .getJSONArray("workouts")
        (0 until raw.length()).map(raw::getJSONObject)
    }

    @Test
    fun theGroupsCountsAndFocusChipsFollowTheFile() {
        val model = model()
        val ready = model.readyWorkouts
        assertEquals(file.size, ready.size)
        assertTrue(ready.size >= 200)
        assertEquals(file.map { it.getString("id") }, ready.map { it.id })
        // The groups in the order their first workout comes, each with its workouts.
        val groups = ReadyLibrary.groups(model.readyShown(ReadyFilter()))
        val expected = file.groupBy { it.getString("group") }
        assertEquals(expected.keys.toList(), groups.map { it.first })
        assertEquals(expected.values.map { it.size }, groups.map { it.second.size })
        assertEquals("problems", groups.map { it.first }.first { it == ReadyLibrary.PROBLEMS })
        // Focus chips: Android's order, only those some workout has.
        val tags = file.flatMap { workout -> workout.optJSONArray("focus")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty() }.toSet()
        assertEquals(ReadyLibrary.FOCUS.filter { it in tags }, model.readyFocuses())
        assertTrue("pull" in model.readyFocuses() && "front_lever" in model.readyFocuses())
    }

    @Test
    fun everyFilterAndTogether() {
        val model = model()
        val ready = model.readyWorkouts
        for (grade in 1..5) {
            assertEquals(file.count { it.optInt("grade") == grade }, model.readyShown(ReadyFilter(grade = grade)).size, "grade $grade")
        }
        for (focus in model.readyFocuses()) {
            val count = file.count { workout -> workout.optJSONArray("focus")?.let { a -> (0 until a.length()).any { a.getString(it) == focus } } == true }
            assertEquals(count, model.readyShown(ReadyFilter(focus = focus)).size, focus)
            assertTrue(count > 0)
        }
        val efforts = Effort.entries.associateWith { effort -> ready.count { model.effortOf(it.id) == effort } }
        assertEquals(ready.size, efforts.values.sum())
        for ((effort, count) in efforts) assertEquals(count, model.readyShown(ReadyFilter(effort = effort)).size, "$effort")
        val both = ReadyFilter(grade = 1, focus = "legs")
        assertEquals(ready.filter { it.grade == 1 && "legs" in it.focusTags }, model.readyShown(both))
        assertTrue(both.filtered)
        assertFalse(ReadyFilter().filtered)
    }

    @Test
    fun theLibraryByTapsWithItsFiltersAndClear() = runComposeUiTest {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Training) }
        onNodeWithText("Library").performClick()
        onNodeWithText("Ready workouts for days off the program. Each has a warm-up, a cool-down and easier options.").assertExists()
        onNodeWithText("${model.readyWorkouts.size} workouts").assertExists()
        onNodeWithText("Clear filters").assertDoesNotExist()
        // Aches & posture opens with its doctor-first note.
        onNodeWithText("ACHES & POSTURE").performScrollTo()
        onNodeWithText("Gentle sessions for common aches", substring = true).assertExists()

        val beginners = model.readyShown(ReadyFilter(grade = 1)).size
        onNodeWithContentDescription("Beginner").performClick()
        onNodeWithText("$beginners workouts").assertExists()
        val legs = model.readyShown(ReadyFilter(grade = 1, focus = "legs")).size
        onNodeWithContentDescription("Legs").performScrollTo().performClick()
        onNodeWithText(if (legs == 1) "1 workout" else "$legs workouts").assertExists()
        onNodeWithContentDescription("Clear filters").performClick()
        onNodeWithText("${model.readyWorkouts.size} workouts").assertExists()

        // An effort no workout has for this athlete says so.
        val empty = Effort.entries.firstOrNull { effort -> model.readyShown(ReadyFilter(effort = effort, grade = 5)).isEmpty() }
        if (empty != null) {
            onNodeWithContentDescription("Elite").performScrollTo().performClick()
            // Android writes "Easy for you" with no-break spaces.
            onNodeWithContentDescription("${empty.name.lowercase().replaceFirstChar(Char::uppercase)} for you").performClick()
            onNodeWithText("No workout matches. Try fewer filters.").assertExists()
        }

        // A card opens its page: meta, the heart, Start workout; no Complete day, no intensity row.
        onNodeWithContentDescription("Clear filters").performClick()
        val first = model.readyWorkouts.first()
        card(first.name.resolve()).performScrollTo().performClick()
        onNodeWithText("START WORKOUT").performScrollTo().assertExists()
        onNodeWithContentDescription("Save to Favorites").assertExists()
        onNodeWithText("How do you feel today?").assertDoesNotExist()
        onNodeWithText("COMPLETE DAY").assertDoesNotExist()
        onNodeWithText("Keep 2–3 reps in reserve", substring = true).assertExists()
    }

    @Test
    fun belowProTheFourFreeOpenAndTheRestOfferPro() = runComposeUiTest {
        val model = model(Tier.PLUS)
        val free = model.readyWorkouts.filter { it.free }
        assertEquals(listOf("ready:neck_at_the_desk", "ready:upper_back_reset", "ready:chest_opener", "ready:low_back_basics"), free.map { it.id })
        assertTrue(free.none { model.readyLocked(it.id) })
        assertTrue(model.readyWorkouts.filterNot { it.free }.all { model.readyLocked(it.id) })
        assertTrue(model(Tier.PRO).readyWorkouts.none { model(Tier.PRO).readyLocked(it.id) })

        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Training, trainingTab = TRAINING_LIBRARY) }
        // A Pro workout wears the tag and opens the offer.
        val pro = model.readyWorkouts.first { !it.free }
        card(pro.name.resolve()).performScrollTo().performClick()
        onNodeWithText("Ready workouts are part of Pro", ignoreCase = true).assertExists()
        onNodeWithText("CANCEL").performClick()
        // A free one opens as any other, and starts.
        card(free[0].name.resolve()).performScrollTo().performClick()
        onNodeWithText("START WORKOUT").performScrollTo().performClick()
        assertEquals(free[0].id, model.training.active()!!.programId)
    }

    @Test
    fun aLockedReadyWorkoutsPageShowsOnlyItsNameAndUnlock() = runComposeUiTest {
        val model = model(Tier.FREE)
        val pro = model.readyWorkouts.first { !it.free }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(pro.id)) }
        onNodeWithContentDescription("Unlock Pro").performClick()
        onNodeWithText("Ready workouts are part of Pro", ignoreCase = true).assertExists()
        onNodeWithText("START WORKOUT").assertDoesNotExist()
        assertEquals(Start.LOCKED, model.start(pro.id))
    }

    @Test
    fun aReadyWorkoutRunsAsWrittenAndNeverMovesTheProgramOn() {
        val model = model()
        // Harder today, every skill and every piece of equipment switched off: the ready workout still runs as written.
        model.intensity = Intensity.HARDER
        val workout = model.readyWorkouts.first { !it.free && it.group == "pull" }
        model.skillSwitches = program.skills!!.families.map { it.spec }.fold(SkillSwitches()) { switches, spec ->
            if (SkillProgressions.LINES[spec.id].isNullOrEmpty()) switches else switches.with(spec.id, SkillProgressions.firstSwitchable(spec))
        }
        model.equipment = EquipmentSwitches(Equipment.entries.toSet())
        assertTrue(model.skillSwitches.any && model.equipment.any)
        val next = model.today().next!!.id
        time -= 40 * 60_000L
        assertEquals(Start.STARTED, model.start(workout.id))
        assertEquals(Intensity.NORMAL, model.training.active()!!.intensity)
        assertEquals(workout.exercises.flatMap { it.sets }.map { it.repetitions },
            model.program(workout.id)!!.exercises.flatMap { it.sets }.map { it.repetitions })
        val state = model.training.state()!!
        assertTrue(state.leftOut.isEmpty())
        assertEquals(state.all.size, state.flow.size)
        // Today shows it while it runs, with Resume.
        assertEquals(workout.id, model.today().next!!.id)
        assertTrue(model.today().resume)

        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            time += 50_000L
            model.training.done(index)
            model.training.skipRest()
        }
        val finished = model.training.finish(6)!!
        assertEquals(workout.id, finished.session.programId)
        assertTrue(finished.points > 0)
        assertTrue(model.points() > 0)
        assertNotNull(model.history().firstOrNull { it.programId == workout.id })
        // The program stays where it was: nothing completed, the same class next.
        assertTrue(model.progress.completedProgramIds().isEmpty())
        assertEquals(next, model.today().next!!.id)
        // It can start again at once.
        assertEquals(Start.STARTED, model.start(workout.id))
    }

    /** A workout's card in the Library, by its name. */
    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.card(name: String) =
        onAllNodesWithContentDescription("$name, ", substring = true).onFirst()

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
