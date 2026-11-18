package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.focus_rest_left
import com.justdataplease.streetgym.app.resources.focus_rest_over
import com.justdataplease.streetgym.app.resources.focus_rest_started
import com.justdataplease.streetgym.app.resources.focus_switch_sides
import com.justdataplease.streetgym.app.resources.focus_time_up
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.ui.WorkoutFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Settings › During your workout (Android: keepScreenAwake, soundCues, workoutView) and what a workout asks of the
 * phone, through a recording stand-in for it: the screen kept on in each view, the beeps and the vibration with the
 * sound on and off, the notification for a rest that ends in the background, and the focus view's spoken cues.
 */
@OptIn(ExperimentalTestApi::class)
class DuringWorkoutTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private var time = 20_775 * 86_400_000L + 12 * 3_600_000L

    /** Records every call a workout makes to the phone. */
    private class Phone : WorkoutDevice {
        val screen = mutableListOf<Boolean>()
        var rests = 0
        val holds = mutableListOf<Boolean>()
        val said = mutableListOf<String>()
        val alerts = mutableListOf<Pair<Long?, String>>()
        override fun keepScreenOn(on: Boolean) { screen += on }
        override fun restOver() { rests++ }
        override fun holdOver(sound: Boolean) { holds += sound }
        override fun announce(text: String) { said += text }
        override fun restAlert(at: Long?, text: String) { alerts += at to text }
    }

    private fun model(store: MemoryStore = MemoryStore(), phone: Phone = Phone()) =
        AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, device = phone, settings = AppSettings(store))

    private fun text(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) = runBlocking { getString(resource, *args) }

    @Test
    fun theSwitchesAreKeptUnderAndroidsNames() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        assertTrue(model.keepScreenAwake)
        assertTrue(model.soundCues)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }

        onNodeWithText("DURING YOUR WORKOUT").assertExists()
        onNodeWithText("Keep screen awake").assertIsOn().performClick()
        onNodeWithText("Keep screen awake").assertIsOff()
        onNodeWithText("Rest timer sound").assertIsOn().performClick()
        onNodeWithText("List").performClick()
        assertEquals("false", store.get("streetgym_settings.keep_screen_awake"))
        assertEquals("false", store.get("streetgym_settings.sound_cues"))
        assertEquals("list", store.get("streetgym_settings.workout_view"))

        // A new start of the app reads them back.
        val again = model(store)
        assertFalse(again.keepScreenAwake)
        assertFalse(again.soundCues)
        assertEquals(WorkoutView.LIST, again.workoutView)
    }

    @Test
    fun theListKeepsTheScreenOnOnlyWhenAskedAndFocusAlways() = runComposeUiTest {
        val phone = Phone()
        val model = model(phone = phone)
        model.keepScreenAwake = false
        model.workoutView = WorkoutView.LIST
        val first = model.today().next!!
        model.start(first.id)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }

        waitForIdle()
        assertEquals(false, phone.screen.last())
        onNodeWithContentDescription("Switch to focus view").performClick()
        waitForIdle()
        assertEquals(true, phone.screen.last())
        // Back to the list with the switch on: on there too.
        model.keepScreenAwake = true
        onNodeWithContentDescription("Switch to list view").performClick()
        waitForIdle()
        assertEquals(true, phone.screen.last())
        model.keepScreenAwake = false
        waitForIdle()
        assertEquals(false, phone.screen.last())
    }

    @Test
    fun theRestTimerSoundBeepsInBothViewsAndATimedSetAlwaysVibrates() {
        val phone = Phone()
        val model = model(phone = phone)
        val training = model.training
        model.start(model.today().next!!.id)
        val flow = training.state()!!.flow
        val timed = flow.steps.indices.first { flow[it].set.repetitions == null && (flow[it].set.durationSeconds ?: 0) > 0 }
        val rested = flow.steps.indices.first { it > timed && (flow[it].set.breakSeconds ?: 0) > 0 && flow.next(it) != null }

        // Sound off: a timed set's end only vibrates, and a rest's end is silent.
        model.soundCues = false
        training.startHold(timed, 1, flow[timed].set.durationSeconds!!)
        time += flow[timed].set.durationSeconds!! * 1_000L
        training.tick()
        assertEquals(listOf(false), phone.holds)
        training.tick(flow[rested].key, true)
        time += flow[rested].set.breakSeconds!! * 1_000L
        training.tick()
        assertEquals(0, phone.rests)

        // Sound on: the tone with the vibration, and the rest's beep, from a tick in the list as from Done in focus.
        model.soundCues = true
        training.startHold(timed, 1, flow[timed].set.durationSeconds!!)
        time += flow[timed].set.durationSeconds!! * 1_000L
        training.tick()
        assertEquals(listOf(false, true), phone.holds)
        training.tick(flow[rested].key, false)
        training.tick(flow[rested].key, true)
        time += flow[rested].set.breakSeconds!! * 1_000L
        training.tick()
        assertEquals(1, phone.rests)
        val next = flow.steps.indices.first { flow[it].key !in training.active()!!.completedSetIds && (flow[it].set.breakSeconds ?: 0) > 0 }
        training.done(next)
        time += flow[next].set.breakSeconds!! * 1_000L
        training.tick()
        assertEquals(2, phone.rests)

        // A rest that ended while the app was away (its notification beeped then) is silent when seen.
        val later = flow.steps.indices.first { flow[it].key !in training.active()!!.completedSetIds && (flow[it].set.breakSeconds ?: 0) > 0 }
        training.done(later)
        time += flow[later].set.breakSeconds!! * 1_000L + 60_000L
        training.tick()
        assertEquals(2, phone.rests)
    }

    @Test
    fun aRestHasItsNotificationForTheBackgroundUntilItEnds() = runComposeUiTest {
        val phone = Phone()
        val model = model(phone = phone)
        val first = model.today().next!!
        model.start(first.id)
        val flow = Training.flow(first)
        val rested = flow.steps.indices.first { (flow[it].set.breakSeconds ?: 0) > 0 && flow.next(it) != null }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }
        for (index in 0 until rested) runOnIdle {
            model.training.done(index)
            model.training.skipRest()
        }
        waitForIdle()
        assertEquals(null, phone.alerts.last().first)

        // Done · Next: the rest starts, with a notification for its end saying what comes next.
        val pause = flow[rested].set.breakSeconds!! * 1_000L
        runOnIdle { model.training.done(rested) }
        waitForIdle()
        val next = flow[rested + 1]
        val words = text(Res.string.focus_rest_over, next.setNumber, next.setCount, next.exercise.name.resolve())
        assertEquals(time + pause to words, phone.alerts.last())
        // +15 s moves it; Skip rest takes it away.
        onNodeWithText("+15 S").performClick()
        waitForIdle()
        assertEquals(time + pause + 15_000L to words, phone.alerts.last())
        onNodeWithText("SKIP REST").performClick()
        waitForIdle()
        assertEquals(null, phone.alerts.last().first)

        // With the sound off there is none; leaving the workout takes any away.
        model.soundCues = false
        val again = flow.steps.indices.first { it > rested && (flow[it].set.breakSeconds ?: 0) > 0 && flow.next(it) != null }
        for (index in rested + 1..again) runOnIdle {
            model.training.done(index)
            if (index < again) model.training.skipRest()
        }
        waitForIdle()
        assertTrue(model.training.state()!!.page is FocusPage.Rest)
        assertEquals(null, phone.alerts.last().first)
        model.soundCues = true
        waitForIdle()
        assertTrue(phone.alerts.last().first != null)
        onNodeWithContentDescription("Switch to list view").performClick()
        onNodeWithContentDescription("Back").performClick()
        waitForIdle()
        assertEquals(null, phone.alerts.last().first)
    }

    @Test
    fun theFocusViewSpeaksTheRestAndTheTimedSetsAndTheListDoesNot() = runComposeUiTest {
        val phone = Phone()
        val model = model(phone = phone)
        val first = model.today().next!!
        model.start(first.id)
        val flow = Training.flow(first)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Workout(first.id)) }
        fun name(index: Int) = flow[index].exercise.name.resolve()
        // The words are read from the app's texts on the way, so each is waited for.
        fun said(expected: String) {
            waitUntil(timeoutMillis = 5_000) { phone.said.lastOrNull() == expected }
            assertEquals(expected, phone.said.last())
        }
        fun quiet(count: Int) {
            waitForIdle()
            Thread.sleep(300)
            assertEquals(count, phone.said.size)
        }

        // The class opens on a timed set: its end is spoken.
        assertTrue(flow[0].set.repetitions == null)
        val hold = flow[0].set.durationSeconds!!
        onNodeWithText("START $hold S").performClick()
        time += hold * 1_000L
        runOnIdle { model.training.tick() }
        said(if (flow[0].sides > 1) text(Res.string.focus_switch_sides)
            else text(Res.string.focus_time_up, flow[0].setNumber, flow[0].setCount, name(0)))

        // A rest: what comes next when it starts, its 15-second marks, and its end.
        val rested = flow.steps.indices.first { (flow[it].set.breakSeconds ?: 0) >= 30 && flow.next(it) != null }
        for (index in 0 until rested) runOnIdle {
            time += 30_000L
            model.training.done(index)
            model.training.skipRest()
        }
        val pause = flow[rested].set.breakSeconds!!
        runOnIdle { model.training.done(rested) }
        val next = flow[rested + 1]
        said(text(Res.string.focus_rest_started, pause, name(rested + 1), next.setNumber))
        val mark = (pause - 1) / 15 * 15
        time += (pause - mark) * 1_000L
        runOnIdle { model.training.tick() }
        said(text(Res.string.focus_rest_left, mark))
        // Said once, not again on the next tick.
        val count = phone.said.size
        runOnIdle { model.training.tick() }
        quiet(count)
        time += mark * 1_000L
        runOnIdle { model.training.tick() }
        said(text(Res.string.focus_rest_over, next.setNumber, next.setCount, name(rested + 1)))

        // The list says nothing.
        onNodeWithContentDescription("Switch to list view").performClick()
        val before = phone.said.size
        val listRest = flow.steps.indices.first { it > rested && (flow[it].set.breakSeconds ?: 0) > 0 && flow.next(it) != null }
        runOnIdle { model.training.tick(flow[listRest].key, true) }
        time += flow[listRest].set.breakSeconds!! * 1_000L
        runOnIdle { model.training.tick() }
        quiet(before)
        onNodeWithText("FINISH WORKOUT").performScrollTo().assertExists()
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
