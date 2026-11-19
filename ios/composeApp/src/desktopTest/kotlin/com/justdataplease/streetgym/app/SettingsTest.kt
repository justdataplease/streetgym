package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.about_body
import com.justdataplease.streetgym.app.resources.app_version_name
import com.justdataplease.streetgym.app.resources.brand_kicker
import com.justdataplease.streetgym.app.resources.inspect_only_note
import com.justdataplease.streetgym.app.resources.reset_body
import com.justdataplease.streetgym.app.resources.safety_body
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentSwitches
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.ExamResult
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.skills.SkillSwitches
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Settings and themes (Android: renderSettings, themePicker, confirmReset, DeveloperMenu, configureWindow, the back
 * gesture): the theme picked by its row and kept after a restart with the status bar following it, Start over clearing
 * what Android clears and keeping the rest, Train safely and About, ten taps on the version and Inspect all days, and
 * the back swipe.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsTest {
    private val time = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun text(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) = runBlocking { getString(resource, *args) }

    /** About's line: the Android app's versionName, which the build writes into the resources. */
    private fun aboutVersion() = "Version ${text(Res.string.app_version_name)}"

    private class Phone : WorkoutDevice {
        val bars = mutableListOf<Boolean>()
        override fun statusBar(dark: Boolean) { bars += dark }
    }

    private fun model(store: MemoryStore, device: WorkoutDevice = WorkoutDevice.None) =
        AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, device = device, settings = AppSettings(store))

    @Test
    fun theThemeIsPickedByItsRowKeptAfterARestartAndTheStatusBarFollows() = runComposeUiTest {
        val store = MemoryStore()
        val phone = Phone()
        val model = model(store, phone)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }

        // Each row speaks its name and summary, the picked one "selected".
        onNodeWithContentDescription("Concrete & Tape, Light · concrete, black lines and hazard tape, selected").assertIsSelected()
        waitForIdle()
        assertEquals(listOf(false), phone.bars)
        onNodeWithContentDescription("Night Park, Dark · asphalt and streetlight orange").performClick()
        assertEquals(StreetTheme.NIGHT_PARK, model.theme)
        assertEquals("night_park", store.get("streetgym_settings.theme"))
        onNodeWithContentDescription("Night Park, Dark · asphalt and streetlight orange, selected").assertIsSelected()
        waitForIdle()
        assertEquals(listOf(false, true), phone.bars)

        // A restart reads it back, as Android keeps it; an Android backup's old dark switch opens on Night Park too.
        assertEquals(StreetTheme.NIGHT_PARK, model(store).theme)
        val legacy = MemoryStore().apply { set("streetgym_settings.dark_mode", "true") }
        assertEquals(StreetTheme.NIGHT_PARK, AppSettings(legacy).theme)
        assertEquals(StreetTheme.CONCRETE, AppSettings(MemoryStore()).theme)
    }

    @Test
    fun startOverClearsTheProgressAndKeepsTheChoices() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        // Classes, history, an exam, a replay and a class in progress; a theme, switches and the placement answered.
        model.progress.completePrograms(ordered.take(130).map { it.id }, at = time - 86_400_000L)
        model.progress.recordExam(ExamResult(ordered.first { it.isExam }.id, time, listOf("pullup"), emptyList()))
        model.goBackTo(1)
        model.start(model.today().next!!.id)
        model.theme = StreetTheme.WHEATPASTE
        model.skillSwitches = SkillSwitches().with("handstand", 0)
        model.equipment = EquipmentSwitches(setOf(Equipment.WALL))
        AppSettings(store).placementDone = true
        val keep = listOf("streetgym_settings.theme", "streetgym_settings.skill_switches", "streetgym_settings.equipment_off")
            .associateWith { store.get(it) }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }

        onNodeWithText(text(Res.string.reset_body)).performScrollTo().assertExists()
        onNodeWithText("RESET PROGRESS").performScrollTo().performClick()
        onNodeWithText("Reset all progress?").assertExists()
        onNodeWithText("CANCEL").performClick()
        assertTrue(model.progress.completedProgramIds().isNotEmpty())
        onNodeWithText("RESET PROGRESS").performScrollTo().performClick()
        onNodeWithText("RESET").performClick()

        // Gone: classes, history, exams, the replay and the class in progress; the strength test is asked again.
        listOf("completed_program_ids", "session_history", "active_workout", "exam_results", "replay_level",
            "replay_repeated_ids", "replay_from_program_id").forEach { assertNull(store.get("streetgym_progress.$it"), it) }
        assertEquals(time.toString(), store.get("streetgym_progress.reset_at"))
        assertEquals("false", store.get("streetgym_settings.placement_done"))
        assertNull(model.training.active())
        assertEquals(0, model.points())
        // Kept: the theme and the switches.
        keep.forEach { (key, value) -> assertEquals(value, store.get(key), key) }
        // Today, with Android's note, asking the strength test again; the sequence is back at the first class.
        onNodeWithText("Progress reset. Choose where to start.").assertExists()
        onNodeWithText("FIRST, A QUICK TEST").assertExists()
        assertTrue(model.placementAsked())
        assertEquals(ordered[0].id, model.today().next!!.id)
    }

    @Test
    fun trainSafelyAndAboutCloseSettings() = runComposeUiTest {
        val model = model(MemoryStore())
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }
        onNodeWithText("TRAIN SAFELY").performScrollTo().assertExists()
        onNodeWithText(text(Res.string.safety_body)).assertExists()
        onNodeWithText("STREET GYM: DAILY CALISTHENICS").performScrollTo().assertExists()
        onNodeWithText(text(Res.string.about_body)).assertExists()
        onNodeWithText(aboutVersion()).assertExists()
        // Android's order of what the iPhone has: Theme, During your workout, Skills, Equipment, Start over, Train safely, About.
        val order = listOf("THEME", "DURING YOUR WORKOUT", "SKILLS IN YOUR CLASSES", "EQUIPMENT YOU HAVE", "START OVER", "TRAIN SAFELY",
            "STREET GYM: DAILY CALISTHENICS").map { onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun tenTapsOnTheVersionOpenTheDeveloperSettings() = runComposeUiTest {
        val store = MemoryStore()
        val model = model(store)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }
        val version = onNodeWithText(aboutVersion()).performScrollTo()
        repeat(3) { version.performClick() }
        onNodeWithText("tap to developer settings", substring = true).assertDoesNotExist()
        version.performClick()
        onNodeWithText("6 taps to developer settings").assertExists()
        repeat(4) { version.performClick() }
        onNodeWithText("2 taps to developer settings").assertExists()
        version.performClick()
        onNodeWithText("1 tap to developer settings").assertExists()
        version.performClick()
        onNodeWithText("Developer settings").assertExists()
        assertEquals("true", store.get("streetgym_settings.developer_unlocked"))
        onNodeWithText("Inspect all days").performClick()
        assertTrue(model.inspectAllDays)
        onNodeWithText("DONE").performClick()
        // Unlocked, one tap opens them again; hiding them switches inspecting off.
        onNodeWithText(aboutVersion()).performClick()
        onNodeWithText("HIDE DEVELOPER SETTINGS").performClick()
        assertFalse(model.inspectAllDays)
        assertEquals("false", store.get("streetgym_settings.developer_unlocked"))
    }

    @Test
    fun inspectingOpensAClassNotReachedReadOnly() = runComposeUiTest {
        val model = model(MemoryStore())
        model.inspectAllDays = true
        val later = ordered[40]
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(later.id)) }
        // The badge over the name, and the greyed button in place of Start.
        assertEquals(2, onAllNodesWithText("INSPECT ONLY").fetchSemanticsNodes().size)
        onNodeWithText(text(Res.string.inspect_only_note)).performScrollTo().assertExists()
        onNodeWithText("START WORKOUT").assertDoesNotExist()
        onNodeWithText("COMPLETE DAY").assertDoesNotExist()
        assertFalse(model.canStart(later.id))
    }

    @Test
    fun aSwipeFromTheLeftEdgeGoesBack() = runComposeUiTest {
        val model = model(MemoryStore().apply { AppSettings(this).placementDone = true })
        val first = model.today().next!!
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Class(first.id)) }
        onNodeWithText("WORKOUT").assertExists()
        // Not from the middle of the screen.
        onRoot().performTouchInput { swipe(Offset(width * 0.4f, height / 2f), Offset(width * 0.95f, height / 2f), 250) }
        onNodeWithText("WORKOUT").assertExists()
        onRoot().performTouchInput { swipe(Offset(2f, height / 2f), Offset(width * 0.7f, height / 2f), 250) }
        onNodeWithText(text(Res.string.brand_kicker)).assertExists()
        onNodeWithText("WORKOUT").assertDoesNotExist()
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
