package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.HistoryCalendar
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.PROGRESS_HISTORY
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.historyDate
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.NextClass
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Progress › History and the streak (Android: HistoryCalendarView, historyRow, StreakUi) over the real program, by taps:
 * a made-up history's month grid and totals, a picked day, the rows' words, stepping and swiping months, the month and
 * day kept across a class, the streak's sheet from Today and from History, and Progress opening after a class.
 */
@OptIn(ExperimentalTestApi::class)
class ProgressTest {
    /** Wednesday 18 Nov 2026, 12:00 UTC. */
    private var time = at(11, 18, 12, 0)

    private fun at(month: Int, day: Int, hour: Int, minute: Int): Long =
        java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, month - 1, day, hour, minute)
        }.timeInMillis

    private fun model(store: MemoryStore = MemoryStore()) =
        AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store))

    /** A class trained from [minutes] before [end] to [end]. */
    private fun train(model: AppModel, program: TrainingProgram, end: Long, minutes: Int = 40, repeat: Boolean = false,
                      intensity: Intensity = Intensity.NORMAL) {
        model.progress.startWorkout(program.id, end - minutes * 60_000L, repeat = repeat, intensity = intensity)
        model.progress.finishWorkout(end, 6, points = 100)
    }

    /**
     * October: three classes on the 20th, 21st and 23rd (a run of three); November: one on the 5th done Harder, five
     * days marked on the 10th, one on the 12th, classes on the 17th and this morning, and a repeat of the first class.
     */
    private fun athlete(): AppModel = model().also { model ->
        train(model, ordered[0], at(10, 20, 18, 0))
        train(model, ordered[1], at(10, 21, 18, 0))
        train(model, ordered[2], at(10, 23, 18, 0))
        train(model, ordered[3], at(11, 5, 19, 30), intensity = Intensity.HARDER)
        model.progress.completePrograms(ordered.subList(4, 9).map { it.id }, at = at(11, 10, 9, 0))
        model.progress.completePrograms(listOf(ordered[9].id), at = at(11, 12, 9, 0))
        train(model, ordered[10], at(11, 17, 18, 0))
        train(model, ordered[11], at(11, 18, 8, 0))
        train(model, ordered[0], at(11, 18, 11, 0), minutes = 30, repeat = true)
    }

    @Test
    fun theMonthCountsTrainedClassesAndListsThemNewestFirst() = runComposeUiTest {
        val model = athlete()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_HISTORY) }

        onNodeWithText("History").assertIsSelected()
        onNodeWithText("NOVEMBER 2026").assertExists()
        // The 5th, 17th, 18th and the repeat: marks count for nothing.
        onNodeWithText("4 workouts this month").assertExists()
        // The rows: the class and when it was finished, a repeat, a Harder class, a single mark and a day of many marks.
        val zone = TimeZone.UTC
        onNodeWithText("Completed ${historyDate(at(11, 18, 8, 0), zone)} • 40 min").assertExists()
        assertEquals("Nov 18, 2026, 8:00 AM", historyDate(at(11, 18, 8, 0), zone))
        assertEquals("Nov 5, 2026, 7:30 PM", historyDate(at(11, 5, 19, 30), zone))
        assertEquals("Nov 12, 2026, 12:05 AM", historyDate(at(11, 12, 0, 5), zone))
        onNodeWithText("Repeat · Completed Nov 18, 2026, 11:00 AM • 30 min").assertExists()
        onNodeWithText("Completed Nov 5, 2026, 7:30 PM • 40 min · Harder").performScrollTo().assertExists()
        onNodeWithText("Marked complete · Nov 12, 2026, 9:00 AM").performScrollTo().assertExists()
        onNodeWithText("5 days marked complete").performScrollTo().assertExists()

        // Each day speaks what it holds.
        onNodeWithContentDescription("November 18, today, 2 workouts, 1 repeat").assertExists()
        onNodeWithContentDescription("November 10, 5 marked complete").assertExists()
        onNodeWithContentDescription("November 11, no workouts").assertExists()
        // A day to come is spoken by its date only, and can't be picked.
        onNodeWithContentDescription("November 25").assertIsNotEnabled()
    }

    @Test
    fun aPickedDayListsOnlyItsClassesUntilTappedAgain() = runComposeUiTest {
        val model = athlete()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_HISTORY) }

        onNodeWithContentDescription("November 17, 1 workout").performClick()
        onNodeWithText("TUESDAY, NOVEMBER 17").assertExists()
        onNodeWithText("Completed Nov 17, 2026, 6:00 PM • 40 min").assertExists()
        onNodeWithText("Completed Nov 18, 2026, 8:00 AM • 40 min").assertDoesNotExist()
        // All of the month brings it back; so does a second tap on the day.
        onNodeWithText("All of November ›").performClick()
        onNodeWithText("Completed Nov 18, 2026, 8:00 AM • 40 min").assertExists()
        onNodeWithContentDescription("November 11, no workouts").performClick()
        onNodeWithText("No workouts on this day.").assertExists()
        onNodeWithContentDescription("November 11, no workouts").performClick()
        onNodeWithText("No workouts on this day.").assertDoesNotExist()
    }

    @Test
    fun theArrowsAndASwipeStepFromTheFirstMonthToThisOne() = runComposeUiTest {
        val model = athlete()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_HISTORY) }

        onNodeWithContentDescription("Next month").assertIsNotEnabled()
        onNodeWithContentDescription("Previous month").performClick()
        onNodeWithText("OCTOBER 2026").assertExists()
        onNodeWithText("3 workouts in October").assertExists()
        // October holds the oldest entry: there is no month before it. The streak's line is about now: not here.
        onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        onNodeWithText("2 DAY STREAK", useUnmergedTree = true).assertDoesNotExist()

        // A sideways swipe over the weeks: right to left is the next month.
        val y = onNodeWithContentDescription("October 21, 1 workout").fetchSemanticsNode().boundsInRoot.center.y
        onRoot().performTouchInput { swipe(Offset(width * 0.8f, y), Offset(width * 0.2f, y), durationMillis = 300) }
        onNodeWithText("NOVEMBER 2026").assertExists()
        onRoot().performTouchInput { swipe(Offset(width * 0.2f, y), Offset(width * 0.8f, y), durationMillis = 300) }
        onNodeWithText("OCTOBER 2026").assertExists()
    }

    @Test
    fun theMonthAndDayStayWhenComingBackFromAClass() = runComposeUiTest {
        val model = athlete()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_HISTORY) }

        onNodeWithContentDescription("Previous month").performClick()
        onNodeWithContentDescription("October 21, 1 workout").performClick()
        // A row opens its class.
        onNodeWithText("Completed Oct 21, 2026, 6:00 PM • 40 min").performScrollTo().performClick()
        assertEquals(2, onAllNodesWithText("REPEAT THIS DAY").fetchSemanticsNodes().size)
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("OCTOBER 2026").assertExists()
        onNodeWithText("WEDNESDAY, OCTOBER 21").assertExists()
    }

    @Test
    fun theStreakSheetOpensFromTodayAndSeeHistoryOpensTheCalendar() = runComposeUiTest {
        val model = athlete()
        setContent { StreetGymApp(model, PaddingValues()) }

        // Trained yesterday and today: two days, lit; the best run was October's three.
        onNodeWithContentDescription("2 day streak. Done for today.").performClick()
        onNodeWithText("Best: 3 days").assertExists()
        onNodeWithText("Done for today.").assertExists()
        onNodeWithText("One rest day between workouts is fine.", substring = true).assertExists()
        onNodeWithText("CLOSE").assertExists()
        onNodeWithText("SEE HISTORY").performClick()

        onNodeWithText("YOUR PROGRESS").assertExists()
        onNodeWithText("History").assertIsSelected()
        onNodeWithText("4 workouts this month").assertExists()
        // History's own line: the plain flame, the streak on tape and the best one; its sheet has no See history.
        onNodeWithText("2 DAY STREAK", useUnmergedTree = true).assertExists()
        onNodeWithText("Best 3", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("2 day streak. Best: 3 days").performClick()
        onNodeWithText("Best: 3 days").assertExists()
        onNodeWithText("SEE HISTORY").assertDoesNotExist()
        onNodeWithText("CLOSE").performClick()
        onNodeWithText("Best: 3 days").assertDoesNotExist()
    }

    @Test
    fun aNewAthletesHistoryIsEmptyWithoutAStreakLine() = runComposeUiTest {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Progress, progressTab = PROGRESS_HISTORY) }
        onNodeWithText("0 workouts this month").assertExists()
        onNodeWithText("Finish a workout and its day is marked here.").assertExists()
        onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        onNodeWithText("Best", substring = true).assertDoesNotExist()
        // The legend names the four marks.
        listOf("Workout", "Repeat", "Your workout", "Marked complete").forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun finishingAClassOpensProgress() = runComposeUiTest {
        val model = model()
        setContent { StreetGymApp(model, PaddingValues()) }
        onNodeWithText("START WORKOUT").performClick()
        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            time += 30_000L
            model.training.done(index)
            model.training.skipRest()
        }
        onNodeWithText("FINISH WORKOUT").performClick()
        onNodeWithText("FINISH").performClick()

        onNodeWithText("Workout completed", substring = true).assertExists()
        onNodeWithText("YOUR PROGRESS").assertExists()
        onNodeWithText("Skills").assertIsSelected()
        onNodeWithContentDescription(" points. Double-tap for how points and ranks work.", substring = true).assertExists()
        // History has the class on today.
        onNodeWithText("History").performClick()
        onNodeWithText("1 workout this month").assertExists()
        assertTrue(HistoryCalendar.dayKey(model.history().single().completedAtEpochMillis, TimeZone.UTC) == 20_261_118)
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
