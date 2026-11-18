package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
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
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.clockText
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The focus workout driven by taps, as the athlete trains the first class: Start workout on Today, a timed set, a set
 * of reps, the rest after it, finishing early with the effort question, and ending a session from the list it opens.
 */
@OptIn(ExperimentalTestApi::class)
class FocusScreenTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    /** An athlete past the strength test, so Today offers the first class. */
    private fun model() = MemoryStore().let { store ->
        AppSettings(store).placementDone = true
        AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store))
    }

    @Test
    fun aClassTrainedByTapsIsSavedWithItsEffort() = runComposeUiTest {
        val model = model()
        val first = model.today().next!!
        val flow = Training.flow(first)
        // The first class opens with a timed set, then a set of reps with a rest after it.
        val hold = flow[0].set.durationSeconds!!
        val rest = flow[1].set.breakSeconds!! * 1_000L
        assertTrue(flow[0].set.repetitions == null && flow[1].set.repetitions != null)
        setContent { StreetGymApp(model, PaddingValues()) }

        onNodeWithText("START WORKOUT").performScrollTo().performClick()
        onNodeWithText("Set 1 of ${flow.size}").assertExists()

        // The timed set: Start counts it down, then Done · Next ticks it.
        onNodeWithText("START $hold S").performClick()
        onNodeWithText(clockText(hold * 1_000L)).assertExists()
        time += hold * 1_000L + 1_000L
        onNodeWithText("DONE · NEXT").performClick()
        onNodeWithText("Set 2 of ${flow.size}").assertExists()

        // The set of reps, then its rest; +15 s, and Skip rest.
        onNodeWithText("DONE · NEXT").performClick()
        onNodeWithText("REST").assertExists()
        onNodeWithText(clockText(rest)).assertExists()
        onNodeWithText("+15 S").performClick()
        onNodeWithText(clockText(rest + 15_000L)).assertExists()
        onNodeWithText("SKIP REST").performClick()
        onNodeWithText("Set 3 of ${flow.size}").assertExists()

        // Finish now: asked first, then how hard it felt.
        onNodeWithText("Finish").performClick()
        onNodeWithText("Finish before every set?").assertExists()
        onNodeWithText("FINISH").performClick()
        onNodeWithText("How hard did that feel?").assertExists()
        onNodeWithText("RPE 8").performClick()
        onNodeWithText("FINISH").performClick()

        // Progress opens, as on Android, with the note; the class is done and the flame lit.
        onNodeWithText("Workout completed", substring = true).assertExists()
        onNodeWithText("1 day streak", substring = true).assertExists()
        assertNull(model.training.active())
        assertTrue(model.isCompleted(first.id))
        val session = model.progress.sessionHistory().single()
        assertEquals(8, session.effortRating)
        assertEquals(2, model.today().next?.number)
    }

    @Test
    fun endSessionFromTheListDropsTheClass() = runComposeUiTest {
        val model = model()
        val first = model.today().next!!
        model.start(first.id)
        // Every set ticked: the summary, which ends a class with Finish workout only, as on Android.
        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            time += 30_000L
            model.training.done(index)
            model.training.skipRest()
        }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }

        onNodeWithText("CLASS COMPLETE").assertExists()
        onNodeWithText("END SESSION").assertDoesNotExist()
        // The title bar's list icon, then End session at the end of the list.
        onNodeWithContentDescription("Switch to list view").performClick()
        onNodeWithText("END SESSION").performScrollTo().performClick()
        onNodeWithText("End this session?").assertExists()
        // Only the dialog's own END SESSION: the screen behind a dialog is hidden from VoiceOver.
        onNodeWithText("END SESSION").performClick()

        // Back on Today: the same class to start again, nothing saved.
        onNodeWithText("START WORKOUT").assertExists()
        assertNull(model.training.active())
        assertFalse(model.isCompleted(first.id))
        assertTrue(model.progress.sessionHistory().isEmpty())
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
