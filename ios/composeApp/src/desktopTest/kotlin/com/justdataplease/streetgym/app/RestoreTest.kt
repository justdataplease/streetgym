package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.Restore
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.Screens
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coming back after the phone closed the app in the background (Android: onSaveInstanceState): the screens and tabs, the
 * builder's unsaved workout, the strength test's counts and the running rest, read back by a new start of the app over
 * the same store; screens that can't open any more are left out; and the shell hands its state to the phone on change.
 */
@OptIn(ExperimentalTestApi::class)
class RestoreTest {
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private class Phone : WorkoutDevice {
        val saved = mutableListOf<String>()
        override fun saveScreens(state: String) { saved += state }
    }

    private fun model(store: MemoryStore, phone: WorkoutDevice = WorkoutDevice.None): AppModel {
        AppSettings(store).placementDone = true
        return AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, device = phone, settings = AppSettings(store),
            library = LibraryStore(store))
    }

    @Test
    fun theScreensTheBuilderTheCountsAndTheRestComeBack() {
        val store = MemoryStore()
        val first = model(store)
        val id = first.today().next!!.id
        first.start(id)
        time += 40_000L
        startRest(first)
        val rest = first.training.runningRest()!!
        first.draft = first.newDraft().copy(name = "Half built")
        first.placementForm.set(0, 12)
        first.placementForm.skill = 3
        val screens = Screens(listOf(Screen.Training, Screen.Level(1), Screen.Class(id), Screen.Workout(id)), 2, "list:x", 1)
        val text = Restore.encode(screens, first)

        // A new start of the app, a few seconds on, the rest still running.
        time += 3_000L
        assertTrue(rest.second > time)
        val again = model(store)
        assertNull(again.training.runningRest())
        assertEquals(screens, Restore.decode(text, again))
        assertEquals(rest, again.training.runningRest())
        assertEquals("Half built", again.draft!!.name)
        assertEquals(listOf(12, -1, -1), again.placementForm.counts.toList())
        assertEquals(3, again.placementForm.skill)

        // The workout ended meanwhile: the screens stop before it; nothing usable: nothing.
        again.training.abandon()
        assertEquals(listOf(Screen.Training, Screen.Level(1), Screen.Class(id)), Restore.decode(text, again)!!.stack)
        assertNull(Restore.decode("not json", again))
        assertNull(Restore.decode("", again))
        // A rest that has ended by then doesn't come back.
        val late = model(store)
        late.start(id)
        time += 10 * 60_000L
        Restore.decode(text, late)
        assertNull(late.training.runningRest())
    }

    @Test
    fun theShellSavesWhereItIsAndOpensThere() = runComposeUiTest {
        val store = MemoryStore()
        val phone = Phone()
        val model = model(store, phone)
        val id = model.today().next!!.id
        model.start(id)
        startRest(model)
        val screens = Screens(listOf(Screen.Today, Screen.Class(id), Screen.Workout(id)), 0, null, 0)
        setContent { StreetGymApp(model, PaddingValues(), restored = screens) }
        onNodeWithText("SKIP REST").assertExists()
        waitForIdle()
        assertTrue(phone.saved.isNotEmpty())
        assertEquals(screens, Restore.decode(phone.saved.last(), model(store)))
    }

    /** Ticks the class's first set with a rest after it, which starts that rest. */
    private fun startRest(model: AppModel) {
        val flow = model.training.state()!!.flow
        val index = flow.steps.indices.first { (flow[it].set.breakSeconds ?: 0) > 0 && flow.next(it) != null }
        model.training.tick(flow[index].key, true)
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
