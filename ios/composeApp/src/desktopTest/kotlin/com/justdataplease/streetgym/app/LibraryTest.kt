package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.DraftProblem
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.Limit
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.Start
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.PROGRESS_HISTORY
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.billing.SavedLimit
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.CustomExercise
import com.justdataplease.streetgym.data.library.LibraryCodec
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Training › My workouts (Android: renderMyWorkouts, heartButton, showListPicker, renderEditDay and LibraryRepository)
 * over the real program: the library kept under Android's key and JSON names and read again after a restart; the
 * limits of each tier; an own workout made, edited, started, trained, finished and deleted; and the heart, the lists and
 * the builder by taps.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    /** An athlete past the strength test, on [tier], with progress, settings and My workouts in [store]. */
    private fun model(store: MemoryStore = MemoryStore(), tier: Tier = Tier.PRO): AppModel {
        AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), tier = tier, now = { time }, zone = TimeZone.UTC, settings = AppSettings(store),
            library = LibraryStore(store))
    }

    /** An own workout of [movements] (searched in the program), named [name], saved through the builder's draft. */
    private fun ownWorkout(model: AppModel, name: String, vararg movements: String): String {
        val draft = model.newDraft()
        model.draft = draft.copy(name = name, exercises = movements.map { query ->
            val movement = model.exercises.search(query).first()
            CustomExercise(movement.key, movement.name, movement.movementId, movement.category, movement.equipment, List(3) { movement.sample })
        })
        assertNull(model.saveDraft())
        return draft.id
    }

    @Test
    fun theLibraryIsKeptUnderAndroidsKeyAndNames() {
        val store = MemoryStore()
        val model = model(store)
        val first = ordered[0]
        model.setSaved(first.id, true)
        val travel = model.createList("  Travel   days ")
        assertEquals("Travel days", travel.name)
        assertTrue(travel.id.startsWith("list:"))
        model.saveToLists(first.id, setOf(travel.id))
        val own = ownWorkout(model, "  Push day ", "push-up", "dip")
        assertTrue(own.startsWith("custom:"))

        // Android's preferences "streetgym_library", key "state": the engine's codec, word for word.
        val raw = store.get("streetgym_library.state")!!
        val json = JSONObject(raw)
        assertEquals(setOf("saved", "days", "lists"), json.keySet())
        val mark = json.getJSONObject("saved").getJSONObject(first.id)
        assertTrue(mark.getBoolean("saved"))
        assertEquals(time, mark.getLong("at"))
        val day = json.getJSONObject("days").getJSONObject(own)
        assertEquals(setOf("id", "name", "updated_at", "deleted", "exercises"), day.keySet())
        assertEquals("Push day", day.getString("name"))
        val exercise = day.getJSONArray("exercises").getJSONObject(0)
        assertEquals(setOf("key", "name", "movement_id", "category", "equipment", "sets"), exercise.keySet())
        assertEquals(setOf("reps", "seconds", "rest"), exercise.getJSONArray("sets").getJSONObject(0).keySet())
        val list = json.getJSONObject("lists").getJSONObject(travel.id)
        assertEquals(setOf("name", "days", "created_at", "updated_at", "deleted"), list.keySet())
        assertEquals(LibraryCodec.decode(json), model.libraryState())

        // After a restart: the same saved day, list and own workout.
        val again = model(store)
        assertTrue(again.isSaved(first.id))
        assertEquals(listOf(first.id), again.libraryState().daysIn(again.visibleLists().single()))
        assertEquals("Push day", again.programById(own)!!.name.resolve())
        assertEquals(2, again.programById(own)!!.exercises.size)
        assertEquals(2, again.libraryState().used)

        // A library Android wrote reads the same: its JSON put back as it came.
        val android = MemoryStore()
        android.set("streetgym_library.state", LibraryCodec.encode(again.libraryState()).toString())
        assertEquals(again.libraryState(), model(android).libraryState())
    }

    @Test
    fun eachTierHasItsLimits() {
        fun saveMany(model: AppModel, count: Int) = ordered.take(count).forEach { model.setSaved(it.id, true) }
        val free = model(tier = Tier.FREE)
        saveMany(free, 2)
        assertNull(free.savedLimit())
        saveMany(free, 3)
        assertEquals(Limit.Offer(Paywall.Saved(3)), free.savedLimit())
        assertEquals(Limit.Offer(Paywall.Lists(0)), free.listLimit())
        // Lists made on Plus stay, hidden, on Free.
        val store = MemoryStore()
        model(store, Tier.PLUS).createList("Travel")
        assertTrue(model(store, Tier.FREE).visibleLists().isEmpty())
        assertEquals(1, model(store, Tier.PLUS).visibleLists().size)

        val plus = model(tier = Tier.PLUS)
        saveMany(plus, 30)
        assertEquals(Limit.Offer(Paywall.Saved(30)), plus.savedLimit())
        repeat(3) { plus.createList("List $it") }
        assertEquals(Limit.Offer(Paywall.Lists(3)), plus.listLimit())
        assertTrue(SavedLimit.canAddToList(Tier.PLUS, 9))
        assertFalse(SavedLimit.canAddToList(Tier.PLUS, 10))

        val pro = model(tier = Tier.PRO)
        saveMany(pro, 299)
        assertNull(pro.savedLimit())
        ownWorkout(pro, "One more", "squat")
        assertEquals(Limit.ProFull(lists = false), pro.savedLimit())
        repeat(10) { pro.createList("List $it") }
        assertEquals(Limit.ProFull(lists = true), pro.listLimit())
        assertFalse(SavedLimit.canAddToList(Tier.PRO, 30))
        // Over the limit after a plan ends: nothing is removed.
        assertTrue(SavedLimit.overLimit(Tier.FREE, pro.libraryState().used))
    }

    @Test
    fun anOwnWorkoutIsMadeStartedTrainedFinishedEditedAndDeleted() {
        val model = model()
        model.intensity = Intensity.HARDER
        val draft = model.newDraft()
        assertEquals(DraftProblem.NAME, model.saveDraft())
        model.draft = draft.copy(name = "Pull day")
        assertEquals(DraftProblem.EXERCISE, model.saveDraft())
        val id = ownWorkout(model, "Pull day", "pull-up", "hollow")
        val own = model.programById(id)!!
        assertEquals(CustomDays.toProgram(model.customDay(id)!!), own)
        assertTrue(model.canStart(id))
        assertNotNull(model.effortOf(id))
        assertNotNull(model.tileOf(id))

        // It starts any time, at Normal whatever today's pick, and takes Today's card while it runs.
        time -= 20 * 60_000L
        assertEquals(Start.STARTED, model.start(id))
        assertEquals(Intensity.NORMAL, model.training.active()!!.intensity)
        val today = model.today()
        assertEquals(id, today.next!!.id)
        assertTrue(today.resume)
        assertNull(today.level)
        assertEquals(own.exercises.flatMap { it.sets }.map { it.repetitions }, today.next!!.exercises.flatMap { it.sets }.map { it.repetitions })

        // Trained to its end: a session with its points, but the program doesn't move on.
        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            time += 60_000L
            model.training.done(index)
            model.training.skipRest()
        }
        val finished = model.training.finish(6)!!
        assertEquals(id, finished.session.programId)
        assertTrue(finished.points > 0)
        assertTrue(model.progress.completedProgramIds().isEmpty())
        assertEquals(ordered[0].id, model.today().next!!.id)
        assertTrue(model.points() > 0)

        // Edited: four sets of the first exercise; the page shows the edit.
        val saved = model.editDraft(id)!!
        model.draft = saved.copy(exercises = saved.exercises.mapIndexed { index, exercise ->
            if (index == 0) exercise.copy(sets = List(4) { exercise.sets.first() }) else exercise
        })
        assertNull(model.saveDraft())
        assertEquals(4, model.programById(id)!!.exercises.first().sets.size)

        // Deleted while in progress: it ends, leaves My workouts and its lists, and its history stays.
        val list = model.createList("Pull")
        model.saveToLists(id, setOf(list.id))
        model.start(id)
        model.deleteCustom(id)
        assertNull(model.training.active())
        assertNull(model.programById(id))
        assertTrue(model.libraryState().days[id]!!.deleted)
        assertTrue(model.libraryState().daysIn(model.visibleLists().single()).isEmpty())
        assertEquals(1, model.history().count { it.programId == id })
    }

    @Test
    fun theHeartOnFreeSavesToFavoritesAndTheLimitOpensTheOffer() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store, Tier.FREE)
        val first = ordered[0]
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(first.id)) }

        onNodeWithContentDescription("Save to Favorites").performClick()
        onNodeWithText("Saved to Favorites").assertExists()
        assertTrue(model.isSaved(first.id))
        onNodeWithContentDescription("Saved to Favorites. Tap to remove.").performClick()
        onNodeWithText("Removed from My workouts").assertExists()
        assertFalse(model.isSaved(first.id))
        onNodeWithContentDescription("Save to Favorites").performClick()

        // My workouts: the day under Saved days; Create and New list wear Plus and open the offer.
        onNodeWithContentDescription("Training").performClick()
        onNodeWithText("My workouts").performClick()
        onNodeWithText("1 of 3 saved and custom days").assertExists()
        onNodeWithContentDescription("${first.name.resolve()}, Level 1 • Workout 1 of 100", substring = true).performScrollTo().assertExists()
        onNodeWithContentDescription("Create a workout Plus").performClick()
        onNodeWithText("Your own workouts are part of Plus", ignoreCase = true).assertExists()
        onNodeWithText("CANCEL").performClick()
        onNodeWithContentDescription("New list, Plus").performClick()
        onNodeWithText("Your own lists are part of Plus", ignoreCase = true).assertExists()
        onNodeWithText("CANCEL").performClick()
        // The card opens the day.
        onNodeWithContentDescription("${first.name.resolve()}, Level 1 • Workout 1 of 100", substring = true).performClick()
        onNodeWithContentDescription("Saved to Favorites. Tap to remove.").assertExists()
    }

    @Test
    fun theFourthSavedDayOnFreeOpensTheOffer() = runComposeUiTest {
        val model = model(tier = Tier.FREE)
        ordered.take(3).forEach { model.setSaved(it.id, true) }
        // A completed class, so its page is open.
        model.completeDay(ordered[3].id)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(ordered[3].id)) }
        onNodeWithContentDescription("Save to Favorites").performClick()
        onNodeWithText("You’ve used all 3 saved and custom days", ignoreCase = true).assertExists()
        assertFalse(model.isSaved(ordered[3].id))
    }

    @Test
    fun listsOnPlusByTaps() = runComposeUiTest {
        val model = model(tier = Tier.PLUS)
        val first = ordered[0]
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(first.id)) }

        // The heart picks lists: Favorites always ticked, a new list made with Add, ticked.
        onNodeWithContentDescription("Save to Favorites").performClick()
        onNodeWithText("Save to lists").assertExists()
        onNodeWithText("Every saved day is in Favorites.").assertExists()
        onNodeWithContentDescription("List name, e.g. Travel").performTextInput("Favorites")
        onNodeWithText("ADD").performClick()
        onNodeWithText("You already have a list called Favorites.").assertExists()
        onNodeWithContentDescription("List name, e.g. Travel").performTextReplacement("Travel")
        onNodeWithText("ADD").performClick()
        onNodeWithText("Travel · 0 days").assertIsOn()
        onNodeWithText("SAVE").performClick()
        onNodeWithText("Saved to Favorites and Travel").assertExists()
        assertTrue(model.isSaved(first.id))
        onNodeWithContentDescription("Saved. Tap to pick its lists.").assertExists()

        // My workouts: the list's chip opens it, with its day; Rename and Delete.
        onNodeWithContentDescription("Training").performClick()
        onNodeWithText("My workouts").performClick()
        onNodeWithContentDescription("Travel").performClick()
        onNodeWithText("RENAME LIST").assertExists()
        onNodeWithContentDescription("${first.name.resolve()}, Level 1", substring = true).assertExists()
        onNodeWithText("RENAME LIST").performClick()
        onNodeWithText("Rename Travel").assertExists()
        onNodeWithContentDescription("List name, e.g. Travel").performTextReplacement("")
        onNodeWithText("SAVE").performClick()
        onNodeWithText("Give the list a name.").assertExists()
        onNodeWithContentDescription("List name, e.g. Travel").performTextReplacement("Trips")
        onNodeWithText("SAVE").performClick()
        onNodeWithContentDescription("Trips").assertExists()
        onNodeWithText("DELETE LIST").performClick()
        onNodeWithText("Delete Trips?").assertExists()
        onNodeWithText("DELETE").performClick()
        onNodeWithContentDescription("Trips").assertDoesNotExist()
        onNodeWithText("CREATE A WORKOUT").assertExists()
        // The day stays in Favorites.
        assertTrue(model.isSaved(first.id))

        // A new list from the chip, opened at once.
        onNodeWithContentDescription("+ New list").performClick()
        onNodeWithText("New list").assertExists()
        onNodeWithContentDescription("List name, e.g. Travel").performTextInput("Park")
        onNodeWithText("CREATE").performClick()
        onNodeWithText("No days in this list yet. Open a day and tap its heart to add it.").assertExists()
    }

    @Test
    fun theBuilderByTapsThenTodayAndHistory() = runComposeUiTest {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Training) }
        onNodeWithText("My workouts").performClick()
        assertEquals(2, onAllNodesWithText("Nothing here yet.").fetchSemanticsNodes().size)
        onNodeWithText("CREATE A WORKOUT").performClick()

        onNodeWithText("YOUR WORKOUT").assertExists()
        onNodeWithText("SAVE WORKOUT").performClick()
        onNodeWithText("Give the workout a name.").assertExists()
        onNodeWithContentDescription("Workout name").performTextInput("Legs at home")
        onNodeWithText("SAVE WORKOUT").performClick()
        onNodeWithText("Add at least one exercise.").assertExists()

        // Add exercise: the search, a row, then its sets.
        onNodeWithText("ADD EXERCISE").performClick()
        onNodeWithText("Add an exercise").assertExists()
        onNodeWithContentDescription("Search: pull-up, core, rings…").performTextInput("zzzz")
        onNodeWithText("No exercise matches.").assertExists()
        val pistol = model.exercises.search("pistol").first()
        onNodeWithContentDescription("Search: pull-up, core, rings…").performTextReplacement("pistol")
        onAllNodesWithContentDescription("${pistol.name}, ", substring = true).onFirst().performClick()
        onNodeWithText("Sets for ${pistol.name}").assertExists()
        onNodeWithContentDescription("Sets").performTextReplacement("4")
        onNodeWithText("APPLY").performClick()
        onNodeWithText("1. ${pistol.name}", ignoreCase = true).assertExists()
        assertEquals(4, model.draft!!.exercises.single().sets.size)
        // A second one, moved up.
        val squat = model.exercises.search("squat").first { it.key != pistol.key }
        onNodeWithText("ADD EXERCISE").performScrollTo().performClick()
        onNodeWithContentDescription("Search: pull-up, core, rings…").performTextInput(squat.name)
        onAllNodesWithContentDescription("${squat.name}, ", substring = true).onFirst().performClick()
        onNodeWithText("APPLY").performClick()
        onAllNodesWithText("UP")[1].performScrollTo().performClick()
        assertEquals(listOf(squat.key, pistol.key), model.draft!!.exercises.map { it.movementKey })
        onNodeWithText("SAVE WORKOUT").performScrollTo().performClick()

        // The workout's page, in place of the builder.
        val id = model.libraryState().customDays.single().id
        onNodeWithText("Your workout · 2 exercises", substring = true).assertExists()
        onNodeWithText("EDIT WORKOUT").assertExists()
        onNodeWithText("How do you feel today?").assertDoesNotExist()
        onNodeWithText("START WORKOUT").performScrollTo().performClick()
        assertEquals(id, model.training.active()!!.programId)
        onNodeWithText("Set 1 of ${model.training.state()!!.flow.size}").assertExists()

        // Today: the own workout takes the card, with Resume and no intensity.
        onNodeWithText("Finish").assertExists()
        model.workoutView = com.justdataplease.streetgym.app.data.WorkoutView.LIST
        onNodeWithContentDescription("Today").performClick()
        onNodeWithText("LEGS AT HOME").assertExists()
        onNodeWithText("RESUME").performScrollTo().assertExists()
        onNodeWithText("How do you feel today?").assertDoesNotExist()

        // Deleted: History reads "Deleted workout" for its sessions.
        time += 30 * 60_000L
        model.training.finish(5)
        model.deleteCustom(id)
        onNodeWithContentDescription("Progress").performClick()
        onNodeWithText("History").performClick()
        onNodeWithText("Deleted workout", ignoreCase = true).performScrollTo().assertExists()
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
