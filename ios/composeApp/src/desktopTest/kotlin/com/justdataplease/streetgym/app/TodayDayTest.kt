package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.LocalDays
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.streak_days
import com.justdataplease.streetgym.app.resources.streak_lit
import com.justdataplease.streetgym.app.resources.streak_none_title
import com.justdataplease.streetgym.app.resources.streak_rest
import com.justdataplease.streetgym.app.ui.roomyStreak
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.Streak
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Today across days and phones (Android: AppSettings.intensityToday, onResume's redraw after midnight, StreakDays'
 * first weekday, todayLine's roomy size): the day's pick kept until the next local day, the flame redrawn for a new day
 * with a stepped clock, the week row starting on the region's first day, and the smaller streak with large text.
 */
@OptIn(ExperimentalTestApi::class)
class TodayDayTest {
    /** 18 Nov 2026 (a Wednesday), 12:00 UTC. */
    private var time = 20_775 * DAY + 12 * 3_600_000L

    private fun model(store: MemoryStore = MemoryStore(), firstWeekday: Int = Streak.MONDAY, zone: TimeZone = TimeZone.UTC) =
        AppModel(program, StoredProgress(store), now = { time }, zone = zone, settings = AppSettings(store), firstWeekday = firstWeekday)

    private fun text(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) = runBlocking { getString(resource, *args) }

    /** Trains today's class to its end and finishes it. */
    private fun trainToday(model: AppModel) {
        model.start(model.today().next!!.id)
        while (true) {
            val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
            time += 30_000L
            model.training.done(index)
            model.training.skipRest()
        }
        model.training.finish(6)
    }

    @Test
    fun todaysPickIsKeptForTheDayAndNormalTheNext() {
        val store = MemoryStore()
        val athens = TimeZone.of("Europe/Athens")
        val model = model(store, zone = athens)
        model.intensity = Intensity.HARDER
        assertEquals("HARDER", store.get("streetgym_settings.intensity"))
        assertEquals(LocalDays.epochDay(time, athens).toString(), store.get("streetgym_settings.intensity_day"))

        // The app closed and opened again the same day: the pick is back, and a class starts with it.
        val again = model(store, zone = athens)
        assertEquals(Intensity.HARDER, again.intensity)
        again.start(again.today().next!!.id)
        assertEquals(Intensity.HARDER, again.training.active()!!.intensity)
        again.training.abandon()

        // After local midnight (21:59 UTC is 23:59 in Athens), Normal again, whether the app restarts or stays open.
        time = 20_775 * DAY + 21 * 3_600_000L + 59 * 60_000L
        assertEquals(Intensity.HARDER, model(store, zone = athens).intensity)
        time += 60_000L
        assertEquals(Intensity.NORMAL, model(store, zone = athens).intensity)
        assertEquals(Intensity.HARDER, again.intensity)
        assertTrue(again.refreshDay())
        assertEquals(Intensity.NORMAL, again.intensity)
        assertFalse(again.refreshDay())
    }

    @Test
    fun afterMidnightTodayDrawsTheFlameForTheNewDay() = runComposeUiTest {
        val model = model()
        trainToday(model)
        setContent { StreetGymApp(model, PaddingValues()) }
        val lit = "${text(Res.string.streak_days, 1)}. ${text(Res.string.streak_lit)}"
        onNodeWithContentDescription(lit).assertExists()

        // Past midnight the screen still shows yesterday's, until Today looks at the clock again.
        time += 13 * 3_600_000L
        onNodeWithContentDescription(lit).assertExists()
        mainClock.advanceTimeBy(31_000L)
        onNodeWithContentDescription("${text(Res.string.streak_days, 1)}. ${text(Res.string.streak_rest)}").assertExists()
        assertEquals(LocalDays.epochDay(time, TimeZone.UTC), model.day)
    }

    @Test
    fun theWeekStartsOnTheRegionsFirstDay() {
        // NSCalendar and java.util.Calendar number the weekdays 1 Sunday to 7 Saturday; Streak 1 Monday to 7 Sunday.
        assertEquals(7, LocalDays.isoWeekday(1))
        assertEquals(1, LocalDays.isoWeekday(2))
        assertEquals(6, LocalDays.isoWeekday(7))

        val monday = model().today().streak.week
        assertEquals(Streak.MONDAY, Streak.weekday(monday.first().epochDay))
        val sunday = model(firstWeekday = LocalDays.isoWeekday(1)).today().streak.week
        assertEquals(7, Streak.weekday(sunday.first().epochDay))
        val saturday = model(firstWeekday = LocalDays.isoWeekday(7)).today().streak.week
        assertEquals(6, Streak.weekday(saturday.first().epochDay))
        // Today (a Wednesday) is in every week row, at its own place.
        assertEquals(listOf(2, 3, 4), listOf(monday, sunday, saturday).map { week -> week.indexOfFirst { it.today } })
    }

    @Test
    fun largeTextGivesTheSmallerFlameAndBoxes() = runComposeUiTest {
        assertTrue(roomyStreak(320.dp, 1f))
        assertTrue(roomyStreak(400.dp, 1.15f))
        assertFalse(roomyStreak(319.dp, 1f))
        assertFalse(roomyStreak(400.dp, 1.3f))

        // The strip on Today at 130% text: Android's smaller sizes, a 29 dp flame (no shadow yet: no streak) and 20 dp
        // boxes 3 dp apart after 8 + 7 dp, so 202 dp wide instead of 240. The athlete is past the strength test.
        val model = model(MemoryStore().apply { AppSettings(this).placementDone = true })
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                StreetGymApp(model, PaddingValues())
            }
        }
        val strip = onNodeWithContentDescription(text(Res.string.streak_none_title), substring = true).getBoundsInRoot()
        assertEquals(202f, strip.width.value, 1f)
    }

    private companion object {
        const val DAY = 86_400_000L
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
