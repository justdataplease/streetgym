package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.LocalDays
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.NextClassCard
import com.justdataplease.streetgym.app.data.PhoneServices
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.WidgetFeed
import com.justdataplease.streetgym.app.data.WidgetLinks
import com.justdataplease.streetgym.app.data.WidgetSnapshot
import com.justdataplease.streetgym.app.data.WidgetText
import com.justdataplease.streetgym.app.data.WidgetWords
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.ui.FlameState
import com.justdataplease.streetgym.app.ui.widgetFigure
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The home-screen widget's snapshot (Android: widget/NextClassCard.kt, NextClassState, WidgetText, WidgetUpdates):
 * what it says for a new athlete, mid-level, during a class, after finishing and across midnight, a locked class, an
 * exam, every class done; when it is written; the JSON Swift reads; the tile's drawing; and the tap's link.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetTest {
    /** 18 Nov 2026, 18:00 in Athens (16:00 UTC). */
    private var time = 20_775 * DAY + 16 * 3_600_000L
    private val athens = TimeZone.of("Europe/Athens")

    private fun model(store: MemoryStore = MemoryStore(), tier: Tier = Tier.PRO, placed: Boolean = true, widget: FakeWidget = FakeWidget()): AppModel {
        AppSettings(store).placementDone = placed
        return AppModel(program, StoredProgress(store), now = { time }, zone = athens, settings = AppSettings(store),
            plans = Plans(store, floor = tier), services = PhoneServices(widget = widget))
    }

    private fun done(model: AppModel, count: Int) =
        model.progress.completePrograms(ordered.take(count).map(TrainingProgram::id), at = time - 30 * DAY)

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
    fun aNewAthleteSeesLevelOneOpeningToday() {
        val model = model(placed = false)
        val snapshot = model.widgetSnapshot(words)
        // The strength test waits on Today, so the tap goes there, not to the first class (Android: NextClassState).
        assertNull(snapshot.card.programId)
        assertEquals(WidgetLinks.TODAY, snapshot.link)
        assertEquals(1, snapshot.card.levelNumber)
        assertEquals(1, snapshot.card.classNumber)
        assertEquals("Next", snapshot.text.tape)
        assertEquals("LEVEL 1 · CLASS 1", snapshot.text.meta)
        assertFalse(snapshot.card.locked)
        // No workout yet: one flame, out, for every day ahead.
        assertEquals(1, snapshot.flames.size)
        assertEquals(FlameState.NONE, snapshot.flames.single().state)
        assertTrue(snapshot.flames.single().spoken.endsWith(". Opens Street Gym."), snapshot.flames.single().spoken)
        // Once the test is answered, the same class opens itself.
        val placed = model(placed = true).widgetSnapshot(words)
        assertEquals(ordered.first().id, placed.card.programId)
        assertEquals(WidgetLinks.of(ordered.first().id), placed.link)
    }

    @Test
    fun midLevelTheCardNamesTheClassAsAndroidDoes() {
        val model = model()
        val index = (12 until 40).first { !ordered[it].isExam && ordered[it].dayType != "recovery" }
        done(model, index)
        val program = ordered[index]
        val snapshot = model.widgetSnapshot(words)
        val card = snapshot.card
        assertEquals(program.id, card.programId)
        assertEquals(program.levelNumber, card.levelNumber)
        assertEquals(program.number, card.classNumber)
        assertEquals(NextClassCard.Kind.CLASS, card.kind)
        // "Role · Lead": the role is the headline; the third line is the lead movement and the minutes.
        val title = program.name.resolve().trim()
        val role = title.substringBefore(" · ").trim()
        assertEquals(role, snapshot.text.headline)
        assertEquals("LEVEL ${program.levelNumber} · CLASS ${program.number}", snapshot.text.meta)
        val lead = program.exercises.first { model.motions == null || model.motions!!.moves(it.movementId, it.name.resolve(), it.category, it.equipment) }
        assertEquals(lead.movementId, card.movementId)
        assertEquals("${lead.name.resolve().trim()} · ${program.estimatedMinutes} min", snapshot.text.detail)
        assertEquals("Next class: Level ${program.levelNumber}, class ${program.number}, $role, ${lead.name.resolve().trim()}, " +
            "${program.estimatedMinutes} min. Opens the class.", snapshot.flames.first().spoken)
        assertEquals("streetgym://class/${program.id}", snapshot.link)
        assertEquals(WidgetLinks.Opening.Class(program.id), WidgetLinks.parse(snapshot.link))
    }

    @Test
    fun anExamListsItsTargetsAndAnAllDoneProgramOpensToday() {
        val model = model()
        val exam = ordered.indexOfFirst(TrainingProgram::isExam)
        done(model, exam)
        val snapshot = model.widgetSnapshot(words)
        assertEquals(NextClassCard.Kind.EXAM, snapshot.card.kind)
        val targets = ordered[exam].tests.size
        assertEquals("$targets ${if (targets == 1) "target" else "targets"} · ${ordered[exam].estimatedMinutes} min", snapshot.text.detail)

        done(model, ordered.size)
        val finished = model.widgetSnapshot(words)
        assertEquals(NextClassCard.Kind.ALL_DONE, finished.card.kind)
        assertEquals("Done", finished.text.tape)
        assertEquals("", finished.text.meta)
        assertEquals("Sequence complete", finished.text.headline)
        assertEquals("Every class done", finished.text.detail)
        assertEquals(WidgetLinks.TODAY, finished.link)
        assertEquals(ordered.last().levelNumber, finished.card.levelNumber)
    }

    @Test
    fun aClassBeyondTheTierKeepsItsLock() {
        val model = model(tier = Tier.FREE)
        done(model, 300)
        val snapshot = model.widgetSnapshot(words)
        assertEquals(4, snapshot.card.levelNumber)
        assertTrue(snapshot.card.locked)
        assertTrue(snapshot.flames.first().spoken.contains(", locked for your tier"), snapshot.flames.first().spoken)
        assertTrue(JSONObject(snapshot.encode()).getBoolean("locked"))
        // Plus opens Level 4: the lock goes, and the snapshot is written again.
        val plus = model(tier = Tier.PLUS).also { done(it, 300) }.widgetSnapshot(words)
        assertFalse(plus.card.locked)
    }

    @Test
    fun aClassInProgressChangesNothingAndFinishingMovesItOnWithTheFlameLit() = runBlocking {
        val widget = FakeWidget()
        val model = model(widget = widget)
        done(model, 12)
        val feed = model.widgetFeed
        assertTrue(feed.offer(model.widgetSnapshot(words), { null }, { null }))
        val before = model.widgetSnapshot(words)
        assertEquals(FlameState.NONE, before.flames.first().state)

        // Started and a set ticked: what the widget shows is the same, so nothing is written (Android: shown).
        model.start(model.today().next!!.id)
        val index = (model.training.state()!!.page as FocusPage.Set).index
        time += 30_000L
        model.training.done(index)
        assertEquals(before, model.widgetSnapshot(words))
        assertFalse(feed.offer(model.widgetSnapshot(words), { null }, { null }))
        assertEquals(1, widget.snapshots.size)

        // Finished: the next class, and today's flame lit.
        model.training.skipRest()
        trainToday(model)
        val after = model.widgetSnapshot(words)
        assertEquals(ordered[13].id, after.card.programId)
        assertEquals(FlameState.LIT, after.flames.first().state)
        assertEquals(1, after.flames.first().streak.days)
        assertTrue(after.flames.first().spoken.endsWith(", 1 day streak. Opens the class."), after.flames.first().spoken)
        assertTrue(feed.offer(after, { null }, { null }))
        assertEquals(2, widget.snapshots.size)
        assertEquals(after.encode(), widget.snapshots.last())
    }

    @Test
    fun theFlamesOfTheComingDaysAreWorkedOutAcrossMidnight() {
        val model = model()
        done(model, 12)
        trainToday(model)
        val today = LocalDays.epochDay(time, athens)
        fun midnight(day: Long) = LocalDate.fromEpochDays(day.toInt()).atStartOfDayIn(athens).toEpochMilliseconds()
        val flames = model.widgetSnapshot(words).flames
        // Lit today; tomorrow unlit with its number (one rest day keeps it); out once two days are missed.
        assertEquals(FlameState.LIT, flames[0].state)
        assertEquals(midnight(today), flames[0].from)
        assertEquals(FlameState.UNLIT, flames[1].state)
        assertEquals(midnight(today + 1), flames[1].from)
        assertEquals(1, flames[1].streak.days)
        assertEquals(FlameState.NONE, flames.last().state)
        assertEquals(0, flames.last().streak.days)
        // Each day's flame is a local midnight later than the one before, in Athens' time.
        flames.zipWithNext().forEach { (a, b) -> assertTrue(b.from > a.from && (b.from - midnight(today)) % (3_600_000L) == 0L) }

        // After midnight the app sees the new day: tomorrow's flame is now today's.
        time = midnight(today + 1) + 60_000L
        model.refreshDay()
        val next = model.widgetSnapshot(words).flames
        assertEquals(flames[1].copy(from = next[0].from), next[0])
        assertEquals(midnight(today + 1), next[0].from)
    }

    @Test
    fun theJsonCarriesTheThemeTheWordsAndTheFlamesColours() {
        val model = model()
        done(model, 12)
        trainToday(model)
        for (theme in StreetTheme.entries) {
            model.theme = theme
            val snapshot = model.widgetSnapshot(words)
            val json = JSONObject(snapshot.encode())
            assertEquals(WidgetSnapshot.VERSION, json.getInt("v"))
            assertEquals(theme.key, json.getString("theme"))
            assertEquals(WidgetSnapshot.displayFontFile(theme), json.getString("font"))
            assertEquals("figure.png", json.getString("figure"))
            assertEquals(snapshot.text.headline, json.getString("headline"))
            assertEquals(theme.shape.upperTitles, json.getBoolean("upper"))
            val colors = json.getJSONObject("colors")
            assertEquals(hex(theme.palette.surface), colors.getString("surface"))
            assertEquals(hex(if (theme == StreetTheme.WHEATPASTE) theme.palette.spot else theme.palette.border), colors.getString("shadow"))
            val flame = json.getJSONArray("flames").getJSONObject(0)
            assertEquals("lit", flame.getString("state"))
            assertEquals(hex(theme.palette.accent), flame.getString("body"))
            // Night Park prints no outline and no shadow; the poster themes do.
            assertEquals(theme == StreetTheme.NIGHT_PARK, flame.isNull("shadow"))
        }
    }

    @Test
    fun theFeedDrawsTheFigureAndHandsTheFaceOnlyWhenTheyChange() = runBlocking {
        val widget = FakeWidget()
        val feed = WidgetFeed(widget)
        val model = model()
        var drawn = 0
        var read = 0
        suspend fun offer() = feed.offer(model.widgetSnapshot(words), { drawn++; androidx.compose.ui.graphics.ImageBitmap(2, 2) },
            { read++; byteArrayOf(1, 2, 3) })
        assertTrue(offer())
        assertEquals(1 to 1, drawn to read)
        assertEquals("anton_regular.ttf", widget.fontFile)
        // The next class with another lead: the figure is drawn again, the face is not.
        done(model, 12)
        assertTrue(offer())
        assertEquals(2 to 1, drawn to read)
        // A new theme: both.
        model.theme = StreetTheme.NIGHT_PARK
        assertTrue(offer())
        assertEquals(3 to 2, drawn to read)
        assertEquals("big_shoulders_black.ttf", widget.fontFile)
        assertFalse(offer())
        assertEquals(3, widget.snapshots.size)
    }

    @Test
    fun theTileHoldsTheLeadMovementsStillPose() {
        val model = model()
        done(model, 12)
        val card = model.widgetCard()
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(3f), LayoutDirection.Ltr)
        val image = widgetFigure(card, StreetTheme.CONCRETE, program.motions!!, emptyMap(), measurer, FontFamily.Default)
        assertEquals(216, image.width)
        val pixels = image.toPixelMap()
        val surface = StreetTheme.CONCRETE.palette.infoSurface
        // The tile's colour, with the figure drawn on it in the theme's main colour.
        assertEquals(surface, pixels[2, 2])
        assertTrue((0 until 216 step 2).any { x -> (0 until 216 step 2).any { y -> pixels[x, y] != surface } })
        val png = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
        assertTrue(png.size > 200)
        assertEquals(0x89.toByte(), png[0])
    }

    @Test
    fun linksKeepAnyClassIdAndNothingElseOpens() {
        listOf("sg:l04:p0020", "ready:neck_at_the_desk", "custom:d1 two/3?").forEach { id ->
            assertEquals(WidgetLinks.Opening.Class(id), WidgetLinks.parse(WidgetLinks.of(id)))
        }
        assertEquals(WidgetLinks.Opening.Today, WidgetLinks.parse("streetgym://today"))
        assertNull(WidgetLinks.parse("com.googleusercontent.apps.123:/oauth2redirect"))
        assertNull(WidgetLinks.parse("https://justdataplease.com"))
    }

    @Test
    fun aTapOnTheWidgetOpensTheClassPageOverToday() = runComposeUiTest {
        val model = model()
        done(model, 12)
        val program = ordered[12]
        setContent { StreetGymApp(model, PaddingValues()) }
        waitForIdle()
        // Today's title bar, then the class page's ("Workout", Android's workout_overview).
        assertTrue(onAllNodesWithText("Workout", ignoreCase = true).fetchSemanticsNodes().isEmpty())
        model.opening = WidgetLinks.parse(WidgetLinks.of(program.id))
        waitForIdle()
        assertNull(model.opening)
        assertTrue(onAllNodesWithText("Workout", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText(program.name.resolve(), ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theWordsAreAndroids() {
        assertEquals("Level 4 · Class 20", WidgetWords.fill(words.levelClass, 4, 20))
        assertEquals("44 min", WidgetWords.fill(words.minutes, 44))
        assertEquals("1 target", WidgetWords.fill(words.targetOne, 1))
        assertEquals("5 targets", WidgetWords.fill(words.targetOther, 5))
        assertEquals("Next class: x. Opens the class.", WidgetWords.fill(words.openClass, "x"))
        val text = WidgetText.of(model().widgetCard(), words)
        assertNotEquals("", text.headline)
    }

    private fun hex(color: androidx.compose.ui.graphics.Color): String =
        "#" + (color.toArgb().toLong() and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')

    private companion object {
        const val DAY = 86_400_000L
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
        val words: WidgetWords by lazy { runBlocking { WidgetWords.load() } }
    }
}
